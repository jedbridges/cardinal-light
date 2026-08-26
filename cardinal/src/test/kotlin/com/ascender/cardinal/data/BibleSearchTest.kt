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
     * A capped page has to reach the end of the Bible, not just the start of
     * it. Filling in canonical order gave every slot to Genesis and Exodus, so
     * a search for a common word could never reach the New Testament however
     * many matches were there. The count was honest about it and that made it
     * worse: the reader was told 310 verses existed and shown a route to none
     * of the ones they wanted.
     */
    @Test fun `a capped page still spans the whole Bible`() = runBlocking {
        val capped = search().search(Translation.WEB, "covenant", limit = 60)
        assertEquals(60, capped.hits.size)
        assertTrue(capped.hits.any { it.book <= 39 }, "no Old Testament hits on the page")
        assertTrue(capped.hits.any { it.book >= 40 }, "the page never reaches the New Testament")
        assertTrue(capped.total > 250, "total must count past the page, got ${capped.total}")

        val everything = search().search(Translation.WEB, "covenant", limit = 400)
        assertEquals(everything.hits.size, capped.total, "total must equal the real match count")
        assertEquals("Showing 60 of ${capped.total}, across the whole Bible", capped.summary)
    }

    /** The page spreads across books, but what is shown still reads in order. */
    @Test fun `a capped page is still canonical`() = runBlocking {
        val hits = search().search(Translation.WEB, "covenant", limit = 60).hits
        val keys = hits.map { it.book * 1_000_000 + it.chapter * 1000 + it.verse }
        assertEquals(keys.sorted(), keys, "a capped page is out of canonical order")
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

    @Test fun `snippet cuts on word boundaries, not mid-word`() {
        // The verse that exposed this: it used to end "...brought th…".
        val verse = "These men are shepherds, for they have been keepers of " +
            "livestock, and they have brought their flocks and their herds " +
            "and all that they have."
        val out = snippet(verse, "shepherds")
        assertTrue(out.contains("shepherds"), out)
        val body = out.trim('…').trim()
        // Every word in the window must be a whole word from the verse.
        body.split(" ").filter { it.isNotBlank() }.forEach { word ->
            assertTrue(
                verse.split(" ").any { it.trim(',', '.', ';', ':') == word.trim(',', '.', ';', ':') },
                "'$word' is a fragment, not a whole word, in: $out",
            )
        }
    }

    @Test fun `snippet still windows when the match sits at the very end`() {
        val verse = "a".repeat(150) + " the final word is needle"
        val out = snippet(verse, "needle")
        assertTrue(out.contains("needle"), out)
        assertTrue(out.startsWith("…"), "head should be marked as cut: $out")
        assertTrue(!out.endsWith("…"), "nothing was cut from the tail: $out")
    }

    @Test fun `snippet never drops the match itself`() = runBlocking {
        // Across a real spread of verses, the window always contains the word.
        search().search(Translation.WEB, "shepherd", limit = 60).hits.forEach {
            val out = snippet(it.text, "shepherd")
            assertTrue(out.contains("shepherd", ignoreCase = true), "lost the match in: $out")
        }
    }
}
