package com.ascender.cardinal.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The corpus contract.
 *
 * These run against the real asset files rather than a fixture, because the
 * thing worth guarding is that the 198 bundled files actually say what the book
 * table claims they say. A generator bug that drops a chapter is invisible
 * until someone opens that chapter on a phone with no network.
 */
class BibleRepositoryTest {

    private val assetsDir = File("src/main/assets")

    private fun repository() = BibleRepository(
        readAsset = { path -> File(assetsDir, path).readBytes() },
    )

    @Test fun `every book file exists for every translation`() {
        val missing = Translation.entries.flatMap { translation ->
            BibleBook.all
                .map { it.assetPath(translation.code) }
                .filterNot { File(assetsDir, it).exists() }
        }
        assertEquals(emptyList(), missing, "missing asset files")
    }

    @Test fun `John 1 verse 1 reads correctly in all three translations`() = runBlocking {
        val repository = repository()
        val opening = mapOf(
            Translation.WEB to "In the beginning was the Word",
            Translation.KJV to "In the beginning was the Word",
            Translation.BSB to "In the beginning was the Word",
        )
        opening.forEach { (translation, expected) ->
            val verse = repository.verse(translation, bookId = 43, chapter = 1, verse = 1)
            assertNotNull(verse, "${translation.code} John 1:1 missing")
            assertTrue(
                verse.text.startsWith(expected),
                "${translation.code} John 1:1 was: ${verse.text.take(60)}",
            )
        }
    }

    @Test fun `chapter counts match the book table`() = runBlocking {
        val repository = repository()
        // Spot-check across the shape of the canon rather than all 66 books:
        // the longest, the shortest, a one-chapter book, and both testaments.
        listOf(1, 19, 40, 43, 65, 66).forEach { bookId ->
            val book = BibleBook.byId(bookId)!!
            val chapters = repository.book(Translation.WEB, bookId).map { it.chapter }.toSet()
            assertEquals(
                (1..book.chapterCount).toSet(),
                chapters,
                "${book.name} chapters",
            )
        }
    }

    @Test fun `Psalm 119 has all 176 verses`() = runBlocking {
        val chapter = repository().chapter(Translation.WEB, bookId = 19, chapter = 119)
        assertEquals(176, chapter.size)
        assertEquals((1..176).toList(), chapter.map { it.verse })
    }

    @Test fun `chapter paging rolls between books and stops at both ends`() {
        val repository = repository()
        assertEquals(Reference(1, 2), repository.nextChapter(1, 1))
        // Genesis has 50 chapters, so the next one is Exodus 1.
        assertEquals(Reference(2, 1), repository.nextChapter(1, 50))
        assertEquals(Reference(65, 1), repository.previousChapter(66, 1))
        assertEquals(null, repository.nextChapter(66, 22), "Revelation 22 is the end")
        assertEquals(null, repository.previousChapter(1, 1), "Genesis 1 is the start")
    }

    @Test fun `the book table covers the whole canon`() {
        assertEquals(66, BibleBook.all.size)
        assertEquals((1..66).toList(), BibleBook.all.map { it.id })
        assertEquals(1189, BibleBook.all.sumOf { it.chapterCount })
        assertEquals(39, BibleBook.old.size)
        assertEquals(27, BibleBook.new.size)
    }

    @Test fun `asset paths match the files on disk`() {
        assertEquals("bible/WEB_43_John.json", BibleBook.byId(43)!!.assetPath("WEB"))
        assertEquals(
            "bible/KJV_22_Song_of_Solomon.json",
            BibleBook.byId(22)!!.assetPath("KJV"),
        )
    }
}
