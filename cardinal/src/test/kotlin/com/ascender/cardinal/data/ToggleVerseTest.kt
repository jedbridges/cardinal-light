package com.ascender.cardinal.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule that a tap must never lose careful work.
 *
 * Reading and marking share a gesture in this app, so an accidental tap is a
 * question of when, not whether. These cases pin the behaviour that makes that
 * survivable: partial marks widen instead of vanishing, and the one removal a
 * tap can perform is reported so it can be undone.
 */
class ToggleVerseTest {

    private val web = Translation.WEB

    private fun wholeVerse(verse: Int) =
        Highlight(book = 43, chapter = 1, verse = verse, translation = "WEB")

    private fun words(verse: Int, start: Int, end: Int) =
        Highlight(43, 1, verse, startWord = start, endWord = end, translation = "WEB")

    @Test fun `tapping an unmarked verse marks the whole verse`() {
        val result = toggleVerseIn(emptyList(), web, 43, 1, 1)
        assertEquals(listOf(wholeVerse(1)), result.highlights)
        assertTrue(result.removed.isEmpty(), "nothing was there to remove")
    }

    @Test fun `tapping a word-range highlight widens it instead of destroying it`() {
        val result = toggleVerseIn(listOf(words(1, 2, 5)), web, 43, 1, 1)
        assertEquals(listOf(wholeVerse(1)), result.highlights)
        assertTrue(result.removed.isEmpty(), "a widen is not a loss, so nothing to undo")
    }

    @Test fun `several word ranges on one verse all widen into a single mark`() {
        val result = toggleVerseIn(listOf(words(1, 0, 1), words(1, 4, 6)), web, 43, 1, 1)
        assertEquals(listOf(wholeVerse(1)), result.highlights)
        assertTrue(result.removed.isEmpty())
    }

    @Test fun `tapping a whole-verse highlight removes it and reports the removal`() {
        val result = toggleVerseIn(listOf(wholeVerse(1)), web, 43, 1, 1)
        assertEquals(emptyList(), result.highlights)
        assertEquals(listOf(wholeVerse(1)), result.removed, "must be undoable")
    }

    @Test fun `a tap never touches other verses`() {
        val others = listOf(wholeVerse(2), words(3, 0, 2))
        val result = toggleVerseIn(others + words(1, 1, 3), web, 43, 1, 1)
        assertTrue(result.highlights.containsAll(others), "neighbours survived")
        assertEquals(3, result.highlights.size)
    }

    @Test fun `a tap never touches the same verse in another chapter or book`() {
        val elsewhere = listOf(
            Highlight(43, 2, 1, translation = "WEB"),
            Highlight(1, 1, 1, translation = "WEB"),
        )
        val result = toggleVerseIn(elsewhere + wholeVerse(1), web, 43, 1, 1)
        assertEquals(elsewhere, result.highlights)
        assertEquals(listOf(wholeVerse(1)), result.removed)
    }

    @Test fun `tapping twice returns to the starting state`() {
        val once = toggleVerseIn(emptyList(), web, 43, 1, 1)
        val twice = toggleVerseIn(once.highlights, web, 43, 1, 1)
        assertEquals(emptyList(), twice.highlights)
    }

    @Test fun `widening then tapping again clears, so the cycle still terminates`() {
        val widened = toggleVerseIn(listOf(words(1, 2, 5)), web, 43, 1, 1)
        val cleared = toggleVerseIn(widened.highlights, web, 43, 1, 1)
        assertEquals(emptyList(), cleared.highlights)
        assertEquals(listOf(wholeVerse(1)), cleared.removed)
    }
}
