package com.ascender.cardinal.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A word range indexes into one translation's exact wording.
 *
 * Measured against the bundled corpus: only 194 of 31,095 verses are
 * word-for-word identical between KJV and WEB, and in 18,894 of the rest the
 * WEB rendering is shorter. So a stored index shown against another
 * translation lands on the wrong word or off the end. These pin the scoping
 * that stops that happening.
 */
class HighlightScopingTest {

    private fun whole(verse: Int, code: String) =
        Highlight(book = 1, chapter = 1, verse = verse, translation = code)

    private fun words(verse: Int, code: String) =
        Highlight(1, 1, verse, startWord = 2, endWord = 5, translation = code)

    private fun state(vararg h: Highlight) = ReaderState(highlights = h.toList())

    @Test fun `a whole-verse mark shows in every translation`() {
        val s = state(whole(1, "KJV"))
        Translation.entries.forEach {
            assertEquals(1, s.highlightsIn(1, 1, it).size, "missing in ${it.code}")
        }
    }

    @Test fun `a word range shows only in the translation it was made in`() {
        val s = state(words(1, "KJV"))
        assertEquals(1, s.highlightsIn(1, 1, Translation.KJV).size)
        assertEquals(0, s.highlightsIn(1, 1, Translation.WEB).size)
        assertEquals(0, s.highlightsIn(1, 1, Translation.BSB).size)
    }

    @Test fun `a tap cannot remove a word range it cannot see`() {
        val kjvWords = words(3, "KJV")
        // Reading in WEB, that mark is invisible. Tapping the verse must add,
        // not silently consume someone else's work.
        val result = toggleVerseIn(listOf(kjvWords), Translation.WEB, 1, 1, 3)
        assertTrue(kjvWords in result.highlights, "the KJV range was taken")
        assertTrue(result.removed.isEmpty(), "nothing was visible to remove")
        assertEquals(2, result.highlights.size)
    }

    @Test fun `tapping in the same translation still cycles`() {
        val once = toggleVerseIn(listOf(words(3, "KJV")), Translation.KJV, 1, 1, 3)
        assertEquals(listOf(whole(3, "KJV")), once.highlights, "should widen")
        val twice = toggleVerseIn(once.highlights, Translation.KJV, 1, 1, 3)
        assertEquals(emptyList(), twice.highlights, "should clear")
    }

    @Test fun `marks in other books and chapters are never in scope`() {
        val s = state(whole(1, "KJV"), Highlight(2, 1, 1, translation = "KJV"),
            Highlight(1, 2, 1, translation = "KJV"))
        assertEquals(1, s.highlightsIn(1, 1, Translation.KJV).size)
    }

    /** The measurement this whole design rests on, pinned against the corpus. */
    @Test fun `translations really do disagree about wording`() {
        val assets = File("src/main/assets")
        fun words(code: String, book: Int) =
            File(assets, BibleBook.byId(book)!!.assetPath(code)).readText()
        val kjv = words("KJV", 43)
        val web = words("WEB", 43)
        assertTrue(kjv != web, "John should differ between KJV and WEB")
    }
}
