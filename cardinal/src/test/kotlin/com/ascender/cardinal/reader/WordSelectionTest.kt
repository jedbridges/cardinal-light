package com.ascender.cardinal.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The selection maths, independent of gestures and layout.
 *
 * These are the cases that decide whether word-level selection across a
 * scrolling chapter is viable at all: hit-testing that survives ragged text,
 * dragging backwards, and a selection that spans verses collapsing into the
 * per-verse word ranges BibleHighlight actually persists.
 */
class WordSelectionTest {

    /** Three verses, four words each, laid out as tidy 100x20 boxes. */
    private fun model(): WordSelectionModel = WordSelectionModel().apply {
        repeat(3) { verse ->
            repeat(4) { word ->
                register(
                    WordId(verse, word),
                    Rect(word * 100f, verse * 40f, word * 100f + 90f, verse * 40f + 20f),
                    "v${verse}w$word",
                )
            }
        }
    }

    @Test fun `hits the word under the point`() {
        assertEquals(WordId(1, 2), model().wordAt(Offset(250f, 50f)))
    }

    @Test fun `falls back to the nearest word in the gap between two words`() {
        // x=95 is in the 10px gutter between word 0 and word 1.
        assertEquals(WordId(0, 0), model().wordAt(Offset(95f, 10f)))
    }

    @Test fun `falls back past the end of a line rather than dropping the drag`() {
        // Dragging beyond the last word must keep selecting, not stall.
        assertEquals(WordId(2, 3), model().wordAt(Offset(900f, 90f)))
    }

    @Test fun `returns nothing far outside any line`() {
        assertNull(model().wordAt(Offset(250f, 5000f)))
    }

    @Test fun `selection spans verses in reading order`() {
        val m = model()
        m.begin(WordId(0, 2))
        m.extendTo(WordId(2, 1))
        assertEquals(
            listOf(WordId(0, 2), WordId(0, 3)) +
                (0..3).map { WordId(1, it) } +
                listOf(WordId(2, 0), WordId(2, 1)),
            m.selection(),
        )
    }

    @Test fun `dragging backwards selects the same range`() {
        val forward = model().apply { begin(WordId(0, 1)); extendTo(WordId(1, 2)) }.selection()
        val backward = model().apply { begin(WordId(1, 2)); extendTo(WordId(0, 1)) }.selection()
        assertEquals(forward, backward)
    }

    @Test fun `a cross-verse selection becomes one word range per verse`() {
        val m = model()
        m.begin(WordId(0, 2))
        m.extendTo(WordId(2, 1))
        assertEquals(
            listOf(
                VerseWordRange(0, 2, 3),
                VerseWordRange(1, 0, 3),
                VerseWordRange(2, 0, 1),
            ),
            m.ranges(),
        )
    }

    @Test fun `selected text joins the words in order`() {
        val m = model()
        m.begin(WordId(1, 0))
        m.extendTo(WordId(1, 2))
        assertEquals("v1w0 v1w1 v1w2", m.selectedText())
    }

    @Test fun `single word selection is one word, not empty`() {
        val m = model()
        m.begin(WordId(1, 1))
        assertEquals(listOf(WordId(1, 1)), m.selection())
        assertEquals(listOf(VerseWordRange(1, 1, 1)), m.ranges())
    }

    @Test fun `clear ends the selection`() {
        val m = model()
        m.begin(WordId(0, 0)); m.extendTo(WordId(1, 1))
        m.clear()
        assertTrue(m.selection().isEmpty())
        assertTrue(!m.isSelecting)
    }

    @Test fun `re-registering a word replaces its frame rather than duplicating`() {
        val m = model()
        m.register(WordId(0, 0), Rect(0f, 500f, 90f, 520f), "moved")
        assertNull(m.wordAt(Offset(10f, 10f))?.takeIf { it == WordId(0, 0) })
        assertEquals(WordId(0, 0), m.wordAt(Offset(10f, 510f)))
    }
}
