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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A word and its character range within the rendered line. */
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
 * Frame for one word, in its own Text's coordinate space.
 *
 * A wrapped word occupies boxes on two lines. Their union would span the full
 * column width and swallow every word between, so measure per line and keep
 * the larger fragment.
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

/** Repeats LightText's internal scaling, which is unavailable to AnnotatedString. */
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
 * One chapter, with tap-to-highlight and long-press word selection.
 *
 * Not lazy: verses vary too much for a uniform item height, and off-screen
 * verses must stay composed so a drag can run past the edge.
 */
@Composable
fun ChapterText(
    verses: List<VerseRecord>,
    highlights: List<Highlight>,
    modifier: Modifier = Modifier,
    scrollToVerse: Int? = null,
    onVerseTap: (Int) -> Unit = {},
    onSelectionChanged: (List<VerseWordRange>) -> Unit = {},
    onSettledAtVerse: (Int) -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    val model = remember(verses) { WordSelectionModel() }
    val layouts = remember(verses) { VerseLayouts() }
    val scrollState = rememberScrollState()

    // Each verse's offset inside the scrolling column, as it is laid out.
    // Snapshot state so the two effects below can wait for it.
    val offsets = remember(verses) { mutableStateMapOf<Int, Int>() }

    // Open on the verse that was asked for. A reference is a coordinate: being
    // told "Genesis 46:32" and landing at 46:1 makes the reader hunt for what
    // the app already knew.
    LaunchedEffect(verses, scrollToVerse) {
        val target = scrollToVerse ?: return@LaunchedEffect
        val offset = snapshotFlow { offsets[target] }.filterNotNull().first()
        scrollState.scrollTo(offset)
    }

    // Report where reading stopped, but only once scrolling settles, so this
    // is a handful of writes per chapter rather than one per frame.
    LaunchedEffect(verses) {
        snapshotFlow { scrollState.isScrollInProgress }
            .filter { !it }
            .collect {
                val top = offsets.entries
                    .filter { it.value <= scrollState.value + TOP_SLOP }
                    .maxByOrNull { it.value }?.key
                if (top != null) onSettledAtVerse(top)
            }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Forces verse rows to recompose; the model is written per drag sample.
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
                        // Edge auto-scroll, so a selection can run past the edge.
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
                    onOffset = { offsets[verse.verse] = it },
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
    onOffset: (Int) -> Unit,
) {
    val text = verse.text
    val prefix = "${verse.verse} "
    val spans = remember(text) { splitWords(text) }

    val colors = LightThemeTokens.colors
    val annotated = remember(text, revision, highlights) {
        // Character ranges over the whole verse, gaps included: styling per
        // word would break the underline at every space. Drag inverts,
        // committed underlines; the theme has no accent to use instead.
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

            .onGloballyPositioned {
                layouts.put(verse.verse) { coords = it }
                onOffset(it.positionInParent().y.toInt())
            },
        onTextLayout = { layouts.put(verse.verse) { layout = it } },
    )
}

/** Character ranges of committed marks, spaces inside a run included. */
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

/** The live drag's range in this verse. Selections are contiguous, so endpoints suffice. */
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
 * Where each verse sits and how its text is laid out. Word frames are derived
 * in [wordAt] for the one verse under the finger; doing it eagerly cost 176
 * verses x ~16 words per scroll frame on Psalm 119.
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
     * The word under [point], registering that verse's frames into [model].
     * Recomputed per call rather than cached: auto-scroll moves the text
     * under the finger mid-drag.
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

/** A verse counts as "top" while its start is just above the fold. */
private const val TOP_SLOP = 8
/** Air between verses. Leaves a short verse just under a 44dp target, on purpose. */
private const val VERSE_VERTICAL_PADDING_UNITS = Space.tight
