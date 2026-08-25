package com.ascender.cardinal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

data class SearchHit(
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
) {
    val reference: String get() = "${BibleBook.byId(book)?.name ?: "?"} $chapter:$verse"
}

data class SearchResults(
    val query: String,
    val hits: List<SearchHit>,
    /** Matches in the whole translation, which may exceed what [hits] holds. */
    val total: Int,
) {
    val truncated: Boolean get() = total > hits.size

    val summary: String get() = when {
        total == 0 -> "No matches"
        truncated -> "Showing ${hits.size} of $total"
        total == 1 -> "1 verse"
        else -> "$total verses"
    }
}

/**
 * Full-text search across a translation.
 *
 * No index. Each book file is read and scanned as raw text first, and only the
 * books that match are parsed into verses. A miss costs a string scan; a hit
 * costs one parse. The whole corpus is 5.4 MB per translation, which is
 * cheaper to walk than an index would be to build on first run and keep
 * migrated afterwards.
 *
 * Search here is submit-driven, not per-keystroke: the LP3 types through a
 * full-screen editor, so the wait lands where it is expected.
 *
 * Cost, measured: 17 ms for the whole corpus on a JVM, and about 2.5 s on the
 * emulator, where 66 asset reads dominate. Two things that look like the cause
 * are not, both tested: parsing (records are now decoded individually, which
 * changed nothing measurable) and asset compression (shipping the assets
 * uncompressed made it slower and added 11 MB). Untested on real hardware.
 */
class BibleSearch(private val readAsset: (String) -> ByteArray) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        translation: Translation,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): SearchResults {
        val needle = query.trim()
        if (needle.length < MIN_QUERY) return SearchResults(needle, emptyList(), 0)

        return withContext(Dispatchers.IO) {
            val hits = mutableListOf<SearchHit>()
            var total = 0

            // Every book is scanned even once the page is full. Results are in
            // canonical order, so a common word fills the page from Genesis
            // and the reader would otherwise have no way to know the New
            // Testament had any at all: "covenant" is 299 verses in the WEB
            // and the first one in Matthew is match 267.
            for (book in BibleBook.all) {
                coroutineContext.ensureActive()

                val raw = runCatching {
                    readAsset(book.assetPath(translation.code)).decodeToString()
                }.getOrNull() ?: continue

                // Cheap reject. Also matches JSON keys, which only costs a
                // needless parse and never a wrong result.
                if (!raw.contains(needle, ignoreCase = true)) continue

                // Parse only the records that matched, not the book. Decoding
                // whole books cost 3.8 MB through the JSON parser for a word
                // like "shepherd" — about five seconds on device. Records are
                // flat and no verse in the corpus contains a brace, so the
                // marker below is an unambiguous record boundary.
                var cursor = 0
                while (true) {
                    val at = raw.indexOf(needle, cursor, ignoreCase = true)
                    if (at < 0) break
                    val start = raw.lastIndexOf(RECORD, at)
                    if (start < 0) { cursor = at + needle.length; continue }

                    var end = raw.indexOf(RECORD, start + RECORD.length)
                    if (end < 0) end = raw.length - 1
                    // Past this record either way, so one verse can only ever
                    // produce one hit however often the word appears in it.
                    cursor = end

                    val record = raw.substring(start, end).trimEnd(']', ',', ' ', '\n')
                    val verse = runCatching {
                        json.decodeFromString<VerseRecord>(record)
                    }.getOrNull() ?: continue

                    // The raw hit may have been in a key rather than the text.
                    if (!verse.text.contains(needle, ignoreCase = true)) continue

                    total++
                    if (hits.size < limit) {
                        hits += SearchHit(book.id, verse.chapter, verse.verse, verse.text)
                    }
                }
            }
            SearchResults(needle, hits, total)
        }
    }

    companion object {
        const val MIN_QUERY = 2
        const val DEFAULT_LIMIT = 60

        /** Start of every record. Cannot occur inside a verse; pinned by test. */
        private const val RECORD = "{\"bookId\""
    }
}

/**
 * A window of the verse around the first match, so a long verse still shows
 * why it matched. Cuts fall on word boundaries: "…they have brought th…" reads
 * like a rendering fault rather than an elision.
 */
fun snippet(text: String, query: String, width: Int = 90): String {
    val at = text.indexOf(query, ignoreCase = true)
    if (at < 0 || text.length <= width) return text

    var start = (at - width / 3).coerceAtLeast(0)
    var end = (start + width).coerceAtMost(text.length)

    // Pull the head forward to the next space, but never past the match.
    if (start > 0) {
        val space = text.indexOf(' ', start)
        if (space in start until at) start = space + 1
    }
    // Pull the tail back to the previous space, but never before the match.
    if (end < text.length) {
        val space = text.lastIndexOf(' ', end)
        if (space >= at + query.length) end = space
    }

    return buildString {
        if (start > 0) append("…")
        append(text.substring(start, end).trim())
        if (end < text.length) append("…")
    }
}
