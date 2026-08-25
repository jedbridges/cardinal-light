package com.ascender.cardinal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One verse, in the asset format shared with the iOS app. */
@Serializable
data class VerseRecord(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
)

/**
 * Reads scripture from the bundled per-book JSON assets.
 *
 * No database: the SDK blocks `android.content.Context` and `buildDatabase`
 * offers no prepackaged-asset hook. Largest of the 198 files is 331 KB.
 * [readAsset] is injected so a JVM test can drive this.
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

        // A bad book file yields an empty chapter rather than crashing the
        // only Bible on a phone with no network.
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

    /** The next chapter, rolling into the next book; null past Revelation 22. */
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
    val display: String get() = chapterReference(bookId, chapter)
}
