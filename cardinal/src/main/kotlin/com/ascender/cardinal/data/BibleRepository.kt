package com.ascender.cardinal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One verse as it sits in the asset files, which are shared with the iOS app. */
@Serializable
data class VerseRecord(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
)

/**
 * Reads scripture out of the bundled per-book JSON assets.
 *
 * There is no database here on purpose. The SDK blocks `android.content.Context`,
 * so raw SQLite is unreachable, and the only way to build a Room database is
 * `buildDatabase`, which exposes no hooks for a prepackaged asset and no
 * migration callbacks at all. Reading a book file directly avoids that whole
 * problem: the largest of the 198 files is 331 KB, decoding one takes a few
 * milliseconds off the main thread, and an LRU of a couple of books covers the
 * way people actually read, which is forwards.
 *
 * [readAsset] is injected rather than taken from a context so this class can be
 * exercised by a plain JVM test.
 */
class BibleRepository(
    private val readAsset: (String) -> ByteArray,
    private val cacheSize: Int = 3,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** Access-ordered, so `removeEldestEntry` evicts the least recently read book. */
    private val cache = object : LinkedHashMap<String, List<VerseRecord>>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<VerseRecord>>) =
            size > cacheSize
    }

    /** Every verse of one book, in order. */
    suspend fun book(translation: Translation, bookId: Int): List<VerseRecord> {
        val book = BibleBook.byId(bookId) ?: return emptyList()
        val key = "${translation.code}:$bookId"

        mutex.withLock { cache[key] }?.let { return it }

        // A missing or malformed book file yields an empty chapter, which the
        // reader renders as "Nothing here." Crashing the only Bible on a phone
        // with no network is the worse failure.
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = readAsset(book.assetPath(translation.code))
                json.decodeFromString<List<VerseRecord>>(bytes.decodeToString())
            }.getOrDefault(emptyList())
        }
        mutex.withLock { cache[key] = loaded }
        return loaded
    }

    /** One chapter, in verse order. Empty if the reference does not exist. */
    suspend fun chapter(translation: Translation, bookId: Int, chapter: Int): List<VerseRecord> =
        book(translation, bookId).filter { it.chapter == chapter }

    suspend fun verse(
        translation: Translation,
        bookId: Int,
        chapter: Int,
        verse: Int,
    ): VerseRecord? = book(translation, bookId)
        .firstOrNull { it.chapter == chapter && it.verse == verse }

    /**
     * The chapter after this one, rolling into the next book, or null at the end
     * of Revelation. [previousChapter] is the mirror image.
     */
    fun nextChapter(bookId: Int, chapter: Int): Reference? {
        val book = BibleBook.byId(bookId) ?: return null
        if (chapter < book.chapterCount) return Reference(bookId, chapter + 1)
        val next = BibleBook.byId(bookId + 1) ?: return null
        return Reference(next.id, 1)
    }

    fun previousChapter(bookId: Int, chapter: Int): Reference? {
        if (chapter > 1) return Reference(bookId, chapter - 1)
        val previous = BibleBook.byId(bookId - 1) ?: return null
        return Reference(previous.id, previous.chapterCount)
    }
}

data class Reference(val bookId: Int, val chapter: Int) {
    val display: String get() = "${BibleBook.byId(bookId)?.name ?: "?"} $chapter"
}
