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
 * Reading position and highlights, in the SDK's DataStore. Not Room:
 * `buildDatabase` exposes no migration hooks, and this data is small and
 * always read whole.
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
     * Tap-to-highlight: nothing, whole verse, nothing. A word-level mark is
     * widened rather than cleared, since reading and marking share a gesture.
     * Returns what it removed, for undo.
     */
    suspend fun toggleVerse(
        translation: Translation,
        bookId: Int,
        chapter: Int,
        verse: Int,
    ): List<Highlight> {
        var removed = emptyList<Highlight>()
        editHighlights { current ->
            val result = toggleVerseIn(current, translation, bookId, chapter, verse)
            removed = result.removed
            result.highlights
        }
        return removed
    }

    /** Commits a drag selection, returning the marks it displaced. */
    suspend fun setWordRanges(
        translation: Translation,
        bookId: Int,
        chapter: Int,
        ranges: List<VerseWordRange>,
    ): List<Highlight> {
        if (ranges.isEmpty()) return emptyList()
        val touched = ranges.map { it.verse }.toSet()
        var replaced = emptyList<Highlight>()
        editHighlights { current ->
            replaced = current.filter {
                it.book == bookId && it.chapter == chapter && it.verse in touched
            }
            current - replaced.toSet() +
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
        return replaced
    }

    /** Deliberate deletion, from the highlights list. */
    suspend fun remove(highlight: Highlight) {
        editHighlights { it - highlight }
    }

    /** Restores marks at the end of the list, so undo puts them back on top. */
    suspend fun restore(highlights: List<Highlight>) {
        if (highlights.isEmpty()) return
        editHighlights { current -> current + highlights.filterNot { it in current } }
    }

    private suspend fun editHighlights(transform: (List<Highlight>) -> List<Highlight>) {
        dataStore.edit { prefs ->
            val current = prefs[HIGHLIGHTS]?.let { decodeHighlights(it) } ?: emptyList()
            prefs[HIGHLIGHTS] = json.encodeToString(transform(current))
        }
    }

    /** A corrupt blob loses marks rather than refusing to open the Bible. */
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

/** What a tap did: the new list, and anything it took away. */
data class ToggleResult(val highlights: List<Highlight>, val removed: List<Highlight>)

/**
 * The tap decision, pure so it can be tested without a DataStore.
 * Partial marks widen; only a whole-verse mark is removed, and reported.
 */
fun toggleVerseIn(
    current: List<Highlight>,
    translation: Translation,
    bookId: Int,
    chapter: Int,
    verse: Int,
): ToggleResult {
    val existing = current.filter { it.isOn(bookId, chapter, verse) }
    val wholeVerse = Highlight(bookId, chapter, verse, translation = translation.code)
    return when {
        existing.isEmpty() ->
            ToggleResult(current + wholeVerse, emptyList())

        existing.any { !it.isWholeVerse } ->
            ToggleResult(current - existing.toSet() + wholeVerse, emptyList())

        else ->
            ToggleResult(current - existing.toSet(), existing)
    }
}
