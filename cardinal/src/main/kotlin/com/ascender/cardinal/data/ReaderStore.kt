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
     * Tap-to-highlight, as a three-step cycle: nothing, whole verse, nothing.
     *
     * A verse carrying a word-level mark is PROMOTED to a whole-verse mark
     * rather than cleared. The earlier version wiped it, which meant one
     * careless tap during reading destroyed a selection someone had dragged out
     * word by word, with no way back. Reading and marking share a gesture here;
     * that gesture must never be the one that loses work.
     *
     * Returns whatever it removed, so the caller can offer an undo. A promotion
     * returns nothing, because nothing was lost.
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

    /**
     * Commits a word-level drag selection, replacing whatever those verses had.
     * Returns the marks it displaced so the caller can offer an undo.
     */
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

    /**
     * Puts back what an undo asks for, at the end of the list.
     *
     * Order is insertion order and the highlights screen reads it newest-first,
     * so a restored mark reappears at the top rather than wherever it used to
     * sit. That is the honest answer: it was just acted on.
     */
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

/** What a tap did: the new list, and anything it took away. */
data class ToggleResult(val highlights: List<Highlight>, val removed: List<Highlight>)

/**
 * The tap-to-highlight decision, as a pure function so it can be tested
 * without a DataStore.
 *
 * The rule that matters: a verse carrying partial marks is widened, never
 * cleared. Only an existing whole-verse mark is removed by a tap, and that
 * removal is reported so it can be undone.
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
