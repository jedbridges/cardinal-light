package com.ascender.cardinal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ascender.cardinal.reader.VerseWordRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Reading position and highlights, kept in the SDK's DataStore.
 *
 * Room is available and would be the reflex choice for highlights, but
 * `buildDatabase` gives no migration hooks whatsoever, so a schema mistake in
 * v1 would leave no clean upgrade path. This data is small and always read
 * whole, so a serialized JSON string in preferences is both simpler and safer.
 * The weather example in the SDK stores its complex values the same way.
 */
class ReaderStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<ReaderState> = dataStore.data.map { prefs ->
        ReaderState(
            translation = prefs[TRANSLATION] ?: Translation.default.code,
            lastBook = prefs[LAST_BOOK] ?: 43,
            lastChapter = prefs[LAST_CHAPTER] ?: 1,
            lastVerse = prefs[LAST_VERSE] ?: 1,
            highlights = prefs[HIGHLIGHTS]?.let { decodeHighlights(it) } ?: emptyList(),
        )
    }

    suspend fun setTranslation(translation: Translation) {
        dataStore.edit { it[TRANSLATION] = translation.code }
    }

    suspend fun setPosition(bookId: Int, chapter: Int, verse: Int = 1) {
        dataStore.edit {
            it[LAST_BOOK] = bookId
            it[LAST_CHAPTER] = chapter
            it[LAST_VERSE] = verse
        }
    }

    /**
     * Tap-to-highlight.
     *
     * Any existing highlight on the verse wins: tapping a verse that carries a
     * word-level highlight clears it rather than adding a whole-verse one on
     * top, which is what a reader expects from a single tap on something
     * already marked.
     */
    suspend fun toggleVerse(translation: Translation, bookId: Int, chapter: Int, verse: Int) {
        editHighlights { current ->
            val existing = current.filter { it.isOn(bookId, chapter, verse) }
            if (existing.isNotEmpty()) {
                current - existing.toSet()
            } else {
                current + Highlight(bookId, chapter, verse, translation = translation.code)
            }
        }
    }

    /** Commits a word-level drag selection, replacing whatever those verses had. */
    suspend fun setWordRanges(
        translation: Translation,
        bookId: Int,
        chapter: Int,
        ranges: List<VerseWordRange>,
    ) {
        if (ranges.isEmpty()) return
        val touched = ranges.map { it.verse }.toSet()
        editHighlights { current ->
            current.filterNot { it.book == bookId && it.chapter == chapter && it.verse in touched } +
                ranges.map {
                    Highlight(
                        book = bookId,
                        chapter = chapter,
                        verse = it.verse,
                        startWord = it.startWord,
                        endWord = it.endWord,
                        translation = translation.code,
                    )
                }
        }
    }

    suspend fun remove(highlight: Highlight) {
        editHighlights { it - highlight }
    }

    suspend fun clearHighlights() {
        editHighlights { emptyList() }
    }

    private suspend fun editHighlights(transform: (List<Highlight>) -> List<Highlight>) {
        dataStore.edit { prefs ->
            val current = prefs[HIGHLIGHTS]?.let { decodeHighlights(it) } ?: emptyList()
            prefs[HIGHLIGHTS] = json.encodeToString(transform(current))
        }
    }

    /**
     * A corrupt or unreadable blob loses highlights rather than bricking the
     * reader. Losing marks is bad; refusing to open the Bible is worse.
     */
    private fun decodeHighlights(raw: String): List<Highlight> =
        runCatching { json.decodeFromString<List<Highlight>>(raw) }.getOrDefault(emptyList())

    private companion object {
        val TRANSLATION = stringPreferencesKey("translation")
        val LAST_BOOK = intPreferencesKey("last_book")
        val LAST_CHAPTER = intPreferencesKey("last_chapter")
        val LAST_VERSE = intPreferencesKey("last_verse")
        val HIGHLIGHTS = stringPreferencesKey("highlights")
    }
}
