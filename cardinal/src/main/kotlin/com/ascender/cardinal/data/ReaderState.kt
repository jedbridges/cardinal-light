package com.ascender.cardinal.data

import kotlinx.serialization.Serializable

/**
 * One highlight.
 *
 * The word-range shape mirrors `BibleHighlight.startWordIndex` /
 * `endWordIndex` in the iOS app, so a selection spanning several verses stores
 * one entry per verse rather than one flat range. Nothing syncs in this tool,
 * but keeping the shape means a future import has somewhere to land.
 *
 * Both indices null means the whole verse.
 */
@Serializable
data class Highlight(
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val startWord: Int? = null,
    val endWord: Int? = null,
    val translation: String,
) {
    val isWholeVerse: Boolean get() = startWord == null || endWord == null

    fun covers(word: Int): Boolean =
        isWholeVerse || (word >= startWord!! && word <= endWord!!)

    fun isOn(book: Int, chapter: Int, verse: Int): Boolean =
        this.book == book && this.chapter == chapter && this.verse == verse

    val reference: String get() = "${BibleBook.byId(book)?.name ?: "?"} $chapter:$verse"
}

/**
 * Everything the reader remembers between launches.
 *
 * Highlights are held in insertion order, so the highlights screen can show
 * newest-first by reversing rather than by carrying a timestamp on every entry.
 */
@Serializable
data class ReaderState(
    val translation: String = Translation.default.code,
    val lastBook: Int = 43,
    val lastChapter: Int = 1,
    val lastVerse: Int = 1,
    val highlights: List<Highlight> = emptyList(),
) {
    val currentTranslation: Translation get() = Translation.fromCode(translation)
    val lastReference: Reference get() = Reference(lastBook, lastChapter)

    fun highlightsOn(book: Int, chapter: Int, verse: Int): List<Highlight> =
        highlights.filter { it.isOn(book, chapter, verse) }

    fun isHighlighted(book: Int, chapter: Int, verse: Int): Boolean =
        highlights.any { it.isOn(book, chapter, verse) }
}
