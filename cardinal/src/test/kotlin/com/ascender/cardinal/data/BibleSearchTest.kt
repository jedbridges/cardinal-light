package com.ascender.cardinal.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Search, against the real corpus rather than a fixture. The point is that it
 * finds the right verses in 31,102 of them, which a fixture cannot show.
 */
class BibleSearchTest {

    private val assets = File("src/main/assets")
    private fun search() = BibleSearch(readAsset = { File(assets, it).readBytes() })

    @Test fun `finds a phrase and reports where it is`() = runBlocking {
        val results = search().search(Translation.WEB, "the light of the world")
        assertTrue(results.hits.isNotEmpty(), "expected hits")
        val refs = results.hits.map { it.reference }
        assertTrue(refs.contains("John 8:12"), "John 8:12 missing from $refs")
    }

    @Test fun `matches regardless of case`() = runBlocking {
        val lower = search().search(Translation.WEB, "shepherd").hits.size
        val upper = search().search(Translation.WEB, "SHEPHERD").hits.size
        val mixed = search().search(Translation.WEB, "ShEpHeRd").hits.size
        assertEquals(lower, upper)
        assertEquals(lower, mixed)
        assertTrue(lower > 20, "expected many shepherd verses, got $lower")
    }

    @Test fun `spans both testaments`() = runBlocking {
        val hits = search().search(Translation.WEB, "covenant", limit = 400).hits
        assertTrue(hits.any { it.book <= 39 }, "no Old Testament hits")
        assertTrue(hits.any { it.book >= 40 }, "no New Testament hits")
    }

    /**
     * Results are canonical, so a common word fills the page from Genesis. The
     * count has to be of the whole translation or the reader is quietly told
     * the New Testament never mentions covenants.
     */
    @Test fun `the count covers the whole Bible even when the page is capped`() = runBlocking {
        val capped = search().search(Translation.WEB, "covenant", limit = 60)
        assertEquals(60, capped.hits.size)
        assertTrue(capped.hits.none { it.book >= 40 }, "premise: the page stops before the NT")
        assertTrue(capped.total > 250, "total must count past the page, got ${capped.total}")

        val everything = search().search(Translation.WEB, "covenant", limit = 400)
        assertEquals(everything.hits.size, capped.total, "total must equal the real match count")
        assertEquals("Showing 60 of ${capped.total}", capped.summary)
    }

    @Test fun `hits come back in canonical order`() = runBlocking {
        val hits = search().search(Translation.WEB, "shepherd").hits
        val keys = hits.map { it.book * 1_000_000 + it.chapter * 1000 + it.verse }
        assertEquals(keys.sorted(), keys, "results are not in canonical order")
    }

    @Test fun `a query that matches nothing returns nothing, not everything`() = runBlocking {
        val results = search().search(Translation.WEB, "zzzqqqx")
        assertTrue(results.hits.isEmpty())
        assertTrue(!results.truncated)
    }

    @Test fun `a one-character query is refused rather than matching the whole Bible`() = runBlocking {
        assertTrue(search().search(Translation.WEB, "a").hits.isEmpty())
        assertTrue(search().search(Translation.WEB, " ").hits.isEmpty())
    }

    @Test fun `a very common word is capped and says so`() = runBlocking {
        val results = search().search(Translation.WEB, "God", limit = 25)
        assertEquals(25, results.hits.size)
        assertTrue(results.truncated, "should report that it stopped early")
        assertTrue(results.summary.startsWith("Showing 25 of "), results.summary)
    }

    @Test fun `the same phrase resolves in every translation`() = runBlocking {
        Translation.entries.forEach { translation ->
            val hits = search().search(translation, "in the beginning").hits
            assertTrue(hits.isNotEmpty(), "${translation.code} found nothing")
            assertTrue(
                hits.any { it.book == 1 && it.chapter == 1 && it.verse == 1 },
                "${translation.code} missed Genesis 1:1",
            )
        }
    }

    @Test fun `snippet windows a long verse around the match and marks the cut`() {
        val verse = "a".repeat(200) + " needle " + "b".repeat(200)
        val out = snippet(verse, "needle")
        assertTrue(out.contains("needle"), "match not in the window")
        assertTrue(out.length < 120, "window too wide: ${out.length}")
        assertTrue(out.startsWith("…") && out.endsWith("…"), "cuts not marked: $out")
    }

    /**
     * Search finds record boundaries by scanning for the literal start of a
     * record rather than parsing whole books, which is what makes it fast. It
     * is only safe while no verse can contain that marker or a stray brace, so
     * that holds here as an invariant of the corpus.
     */
    @Test fun `no verse anywhere contains a brace, which record scanning relies on`() {
        var checked = 0
        Translation.entries.forEach { translation ->
            BibleBook.all.forEach { book ->
                val raw = File(assets, book.assetPath(translation.code)).readText()
                // Braces only ever delimit records: two per record, no more.
                val opens = raw.count { it == '{' }
                val closes = raw.count { it == '}' }
                val records = Regex("""\{"bookId"""").findAll(raw).count()
                assertEquals(records, opens, "${'$'}{book.name} ${'$'}{translation.code}: stray '{'")
                assertEquals(records, closes, "${'$'}{book.name} ${'$'}{translation.code}: stray '}'")
                checked++
            }
        }
        assertEquals(198, checked, "should have checked every bundled file")
    }

    @Test fun `snippet leaves a short verse alone`() {
        assertEquals("Jesus wept.", snippet("Jesus wept.", "wept"))
    }
}
