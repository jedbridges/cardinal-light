package com.ascender.cardinal.data

import kotlinx.serialization.Serializable

/**
 * One highlight. Both indices null means the whole verse.
 *
 * The word-range shape mirrors iOS `BibleHighlight`, so a multi-verse
 * selection stores one entry per verse. Nothing syncs, but a future import
 * has somewhere to land.
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

    val reference: String get() = verseReference(book, chapter, verse)
}

/** What the reader remembers between launches. Highlights in insertion order. */
@Serializable
data class ReaderState(
    val translation: String = Translation.default.code,
    val lastBook: Int = 43,
    val lastChapter: Int = 1,
    val lastVerse: Int = 1,
    val highlights: List<Highlight> = emptyList(),
) {
    val currentTranslation: Translation get() = Translation.fromCode(translation)

    /**
     * Marks to draw on a chapter in a given translation.
     *
     * A whole-verse mark is about the verse, so it shows in every translation.
     * A word range is about particular words, and the translations do not share
     * them: 99.4% of verses differ in wording between KJV and WEB, and in most
     * of those the other rendering is shorter, so a stored index would land on
     * the wrong word or off the end. Word ranges therefore show only in the
     * translation they were made in.
     */
    fun highlightsIn(book: Int, chapter: Int, translation: Translation): List<Highlight> =
        highlights.filter {
            it.book == book && it.chapter == chapter &&
                (it.isWholeVerse || it.translation == translation.code)
        }
}
