package com.ascender.cardinal.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.ascender.cardinal.data.Highlight
import com.ascender.cardinal.data.VerseRecord
import com.ascender.cardinal.reader.VerseWordRange
import com.ascender.cardinal.reader.WordId
import com.ascender.cardinal.reader.WordSelectionModel
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch

/** A word and the character range it occupies inside its rendered line. */
private data class WordSpan(val index: Int, val start: Int, val end: Int)

private fun splitWords(text: String): List<WordSpan> {
    val spans = mutableListOf<WordSpan>()
    var i = 0
    var w = 0
    while (i < text.length) {
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) break
        val start = i
        while (i < text.length && !text[i].isWhitespace()) i++
        spans += WordSpan(w++, start, i)
    }
    return spans
}

/**
 * Frame for one word, in the coordinate space of its own Text.
 *
 * A word that wraps produces boxes on two lines, and their union would be a
 * rectangle spanning the full column width, swallowing every word between. So
 * the fragments are measured per line and the larger one wins: hit-testing
 * stays honest and the selection does not jump.
 */
private fun wordFrame(layout: TextLayoutResult, start: Int, end: Int): Rect? {
    if (start >= end) return null
    val byLine = LinkedHashMap<Int, Rect>()
    for (offset in start until end) {
        val line = layout.getLineForOffset(offset)
        val box = layout.getBoundingBox(offset)
        byLine[line] = byLine[line]?.let {
            Rect(
                minOf(it.left, box.left), minOf(it.top, box.top),
                maxOf(it.right, box.right), maxOf(it.bottom, box.bottom),
            )
        } ?: box
    }
    return byLine.values.maxByOrNull { it.width }
}

/**
 * Light's typography tokens are authored in design pixels against a 600 px
 * baseline and scaled at render time. `LightText` does that internally, but it
 * only takes a `String`, and scripture needs an `AnnotatedString` so verse
 * numbers and highlights can live inside the same flowing paragraph. This
 * repeats the scaling that `LightText` would have done.
 */
@Composable
private fun TextUnit.scaled(): TextUnit =
    if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()

@Composable
fun scriptureStyle(): TextStyle {
    val base = LightThemeTokens.typography.paragraphWide
    return base.copy(
        fontSize = base.fontSize.scaled(),
        lineHeight = base.lineHeight.scaled(),
        letterSpacing = base.letterSpacing.scaled(),
    )
}

/**
 * One chapter, scrollable, with tap-to-highlight and long-press word selection.
 *
 * Deliberately not lazy. `LightLazyScrollView` needs a uniform item height to
 * size its scrollbar and verses vary from two words to a paragraph, and word
 * frames registered by an off-screen verse are exactly what makes a selection
 * survive a drag past the bottom edge. A chapter is a bounded amount of text;
 * the worst case in the whole Bible is Psalm 119 at 176 verses.
 */
@Composable
fun ChapterText(
    verses: List<VerseRecord>,
    highlights: List<Highlight>,
    modifier: Modifier = Modifier,
    onVerseTap: (Int) -> Unit = {},
    onSelectionChanged: (List<VerseWordRange>) -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    val model = remember(verses) { WordSelectionModel() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Bumped whenever the selection changes, to force the verse rows to
    // recompose. The model itself is deliberately not observable: it is written
    // on every drag sample, and making it snapshot state would recompose the
    // whole chapter per frame.
    var revision by remember { mutableIntStateOf(0) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    fun publish() {
        revision++
    }

    LightScrollView(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .pointerInput(verses) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        model.wordAt(local + rootOrigin)?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            model.begin(it)
                            publish()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        model.wordAt(change.position + rootOrigin)?.let {
                            if (it != model.head) {
                                model.extendTo(it)
                                publish()
                            }
                        }
                        // Edge auto-scroll: a chapter is taller than the screen,
                        // so a selection that cannot run past the bottom edge is
                        // not a selection you can actually use.
                        val height = size.height
                        val edge = height * 0.12f
                        val dy = when {
                            change.position.y > height - edge -> AUTO_SCROLL_STEP
                            change.position.y < edge -> -AUTO_SCROLL_STEP
                            else -> 0f
                        }
                        if (dy != 0f) scope.launch { scrollState.scrollBy(dy) }
                    },
                    onDragEnd = {
                        model.end()
                        val ranges = model.ranges()
                        if (ranges.isNotEmpty()) onSelectionChanged(ranges)
                        model.clear()
                        publish()
                    },
                    onDragCancel = {
                        model.clear()
                        publish()
                    },
                )
            },
        scrollState = scrollState,
    ) {
        Column(modifier = Modifier.padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
            verses.forEach { verse ->
                SelectableVerse(
                    verse = verse,
                    highlights = highlights.filter { it.verse == verse.verse },
                    model = model,
                    revision = revision,
                    onTap = { onVerseTap(verse.verse) },
                )
            }
            footer()
        }
    }
}

@Composable
private fun SelectableVerse(
    verse: VerseRecord,
    highlights: List<Highlight>,
    model: WordSelectionModel,
    revision: Int,
    onTap: () -> Unit,
) {
    val text = verse.text
    val prefix = "${verse.verse} "
    val spans = remember(text) { splitWords(text) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Re-register whenever geometry OR scroll position changes: the frames live
    // in root coordinates, so scrolling invalidates every one of them.
    fun registerAll() {
        val currentLayout = layout ?: return
        val origin = coords?.positionInRoot() ?: return
        spans.forEach { span ->
            wordFrame(currentLayout, span.start + prefix.length, span.end + prefix.length)?.let {
                model.register(
                    WordId(verse.verse, span.index),
                    it.translate(origin.x, origin.y),
                    text.substring(span.start, span.end),
                )
            }
        }
    }

    val colors = LightThemeTokens.colors
    val annotated = remember(text, revision, highlights) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.contentSecondary)) { append(prefix) }
            var cursor = 0
            spans.forEach { span ->
                append(text.substring(cursor, span.start))
                val word = text.substring(span.start, span.end)
                val id = WordId(verse.verse, span.index)
                val style = when {
                    // The live drag inverts, which is unmissable and correctly
                    // reads as transient. A committed highlight underlines: on a
                    // monochrome panel an inverted block that never goes away
                    // fights the surrounding text for the eye.
                    model.isSelected(id) ->
                        SpanStyle(background = colors.content, color = colors.background)

                    highlights.any { it.covers(span.index) } ->
                        SpanStyle(textDecoration = TextDecoration.Underline)

                    else -> null
                }
                if (style != null) withStyle(style) { append(word) } else append(word)
                cursor = span.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Text(
        text = annotated,
        style = scriptureStyle(),
        color = colors.content,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onTap)
            .padding(vertical = VERSE_VERTICAL_PADDING_UNITS.gridUnitsAsDp())
            .onGloballyPositioned { coords = it; registerAll() },
        onTextLayout = { layout = it; registerAll() },
    )
}

private const val AUTO_SCROLL_STEP = 24f
private const val VERSE_VERTICAL_PADDING_UNITS = 0.25f
