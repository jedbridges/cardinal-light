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
    val layouts = remember(verses) { VerseLayouts() }
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
                        layouts.wordAt(local + rootOrigin, model, verses)?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            model.begin(it)
                            publish()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        layouts.wordAt(change.position + rootOrigin, model, verses)?.let {
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
                    layouts = layouts,
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
    layouts: VerseLayouts,
    revision: Int,
    onTap: () -> Unit,
) {
    val text = verse.text
    val prefix = "${verse.verse} "
    val spans = remember(text) { splitWords(text) }

    val colors = LightThemeTokens.colors
    val annotated = remember(text, revision, highlights) {
        // Styling per word would break the underline at every space, which
        // reads as a dotted line rather than a marked passage. So the marks are
        // resolved to character ranges over the whole verse first, gaps
        // included, and the string is emitted in runs of constant style.
        //
        // The live drag inverts, which is unmissable and correctly reads as
        // transient. A committed highlight underlines: on a monochrome panel an
        // inverted block that never goes away fights the surrounding text.
        val selected = selectionRange(spans, model, verse.verse)
        val marked = highlightRanges(text, spans, highlights)
        val inverted = SpanStyle(background = colors.content, color = colors.background)
        val underlined = SpanStyle(textDecoration = TextDecoration.Underline)

        fun styleAt(index: Int): SpanStyle? = when {
            selected != null && index in selected -> inverted
            marked.any { index in it } -> underlined
            else -> null
        }

        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.contentSecondary)) { append(prefix) }
            var start = 0
            while (start < text.length) {
                val style = styleAt(start)
                var end = start + 1
                while (end < text.length && styleAt(end) == style) end++
                val chunk = text.substring(start, end)
                if (style != null) withStyle(style) { append(chunk) } else append(chunk)
                start = end
            }
        }
    }

    Text(
        text = annotated,
        style = scriptureStyle(),
        color = colors.content,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClickLabel = "Highlight verse ${verse.verse}", onClick = onTap)
            .padding(vertical = VERSE_VERTICAL_PADDING_UNITS.gridUnitsAsDp())
            // Publishing the layout is a map write. Turning it into word frames
            // is deferred to VerseLayouts.wordAt, which only ever does it for
            // the one verse under a finger.
            .onGloballyPositioned { layouts.put(verse.verse) { coords = it } },
        onTextLayout = { layouts.put(verse.verse) { layout = it } },
    )
}

/**
 * Character ranges covered by committed highlights. A whole-verse mark spans
 * the verse; a word-range mark spans from the first word's first character to
 * the last word's last, so the spaces inside the run are underlined too.
 */
private fun highlightRanges(
    text: String,
    spans: List<WordSpan>,
    highlights: List<Highlight>,
): List<IntRange> = highlights.mapNotNull { highlight ->
    if (highlight.isWholeVerse) return@mapNotNull text.indices
    val first = spans.getOrNull(highlight.startWord ?: return@mapNotNull null) ?: return@mapNotNull null
    val last = spans.getOrNull(highlight.endWord ?: return@mapNotNull null) ?: return@mapNotNull null
    first.start until last.end
}

/**
 * The character range of the live drag inside this verse. A selection is a
 * range over ordered word ids, so whatever it touches in one verse is
 * contiguous and the endpoints are enough.
 */
private fun selectionRange(
    spans: List<WordSpan>,
    model: WordSelectionModel,
    verse: Int,
): IntRange? {
    val touched = spans.filter { model.isSelected(WordId(verse, it.index)) }
    if (touched.isEmpty()) return null
    return touched.first().start until touched.last().end
}

/**
 * Where each verse ended up, and what its text layout is.
 *
 * The first version of this registered a frame for every word of every verse
 * from `onGloballyPositioned`, which fires on each scroll frame. On Psalm 119
 * that is 176 verses times roughly sixteen words, recomputed continuously, and
 * it showed up as 100% janky frames and a 1.6s first paint.
 *
 * Verses now publish only their coordinates and layout, which is a map write.
 * Word frames are computed in [wordAt], for the single verse under the finger,
 * and only while a drag is actually happening. Reading costs nothing;
 * selecting costs one verse.
 */
private class VerseLayouts {
    class Entry {
        var coords: LayoutCoordinates? = null
        var layout: TextLayoutResult? = null
    }

    private val entries = LinkedHashMap<Int, Entry>()

    fun put(verse: Int, update: Entry.() -> Unit) {
        entries.getOrPut(verse) { Entry() }.update()
    }

    fun clear() = entries.clear()

    /**
     * The word under [point] in root coordinates, registering that verse's
     * frames into [model] so the selection maths can work with them.
     *
     * Frames are re-registered on every call rather than cached, because
     * auto-scroll moves the text under the finger mid-drag.
     */
    fun wordAt(point: Offset, model: WordSelectionModel, verses: List<VerseRecord>): WordId? {
        val verse = verseAt(point) ?: nearestVerse(point) ?: return null
        val entry = entries[verse] ?: return null
        val layout = entry.layout ?: return null
        val origin = entry.coords?.positionInRoot() ?: return null
        val text = verses.firstOrNull { it.verse == verse }?.text ?: return null
        val prefix = "$verse ".length

        splitWords(text).forEach { span ->
            wordFrame(layout, span.start + prefix, span.end + prefix)?.let {
                model.register(
                    WordId(verse, span.index),
                    it.translate(origin.x, origin.y),
                    text.substring(span.start, span.end),
                )
            }
        }
        return model.wordAt(point)
    }

    private fun verseAt(point: Offset): Int? = entries.entries.firstOrNull { (_, entry) ->
        val coords = entry.coords ?: return@firstOrNull false
        if (!coords.isAttached) return@firstOrNull false
        val top = coords.positionInRoot().y
        point.y >= top && point.y <= top + coords.size.height
    }?.key

    /** Dragging past the last verse should keep selecting, not stall. */
    private fun nearestVerse(point: Offset): Int? = entries.entries
        .filter { it.value.coords?.isAttached == true }
        .minByOrNull { (_, entry) ->
            val coords = entry.coords!!
            val top = coords.positionInRoot().y
            val bottom = top + coords.size.height
            when {
                point.y < top -> top - point.y
                point.y > bottom -> point.y - bottom
                else -> 0f
            }
        }?.key
}

private const val AUTO_SCROLL_STEP = 24f
/**
 * Air between verses.
 *
 * Was a quarter unit, which is 3.8dp and left a one-line verse as a ~38dp
 * target. Half a unit both reads better as prose and grows the target. It
 * still does not reach 44dp for the shortest verses, and that is a deliberate
 * limit: forcing every verse to a button-sized target would space scripture
 * like a list and cost the thing the app exists for. A stray tap is survivable
 * now that it widens rather than destroys, and is undoable either way.
 */
private const val VERSE_VERTICAL_PADDING_UNITS = Space.tight
