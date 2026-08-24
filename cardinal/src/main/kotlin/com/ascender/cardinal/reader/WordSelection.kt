package com.ascender.cardinal.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/**
 * Word-level selection across a scrolling chapter.
 *
 * This mirrors the iOS TextSelectionManager rather than reinventing it: words
 * register a frame in one shared coordinate space, a point is hit-tested
 * against that registry, and a selection is the ordered range between two word
 * ids. That design is already proven in the shipping app.
 *
 * One deliberate simplification. iOS derives reading order at selection time by
 * grouping word frames into lines by their mid-Y and sorting each line by X,
 * because its word ids are opaque strings. Here an id carries its verse and
 * word index, so order is total and known up front. Geometry decides only WHICH
 * word you are touching, never what comes after it. That removes a whole class
 * of bug around wrapped lines and mixed text sizes.
 */
data class WordId(val verse: Int, val word: Int) : Comparable<WordId> {
    override fun compareTo(other: WordId): Int =
        compareValuesBy(this, other, WordId::verse, WordId::word)
}

data class WordFrame(val id: WordId, val frame: Rect, val text: String)

class WordSelectionModel {
    private val frames = LinkedHashMap<WordId, WordFrame>()

    var anchor: WordId? = null
        private set
    var head: WordId? = null
        private set

    val isSelecting: Boolean get() = anchor != null

    /** Registered by each verse as Compose lays it out. Idempotent per id. */
    fun register(id: WordId, frame: Rect, text: String) {
        frames[id] = WordFrame(id, frame, text)
    }

    fun clearFrames() = frames.clear()

    fun frameFor(id: WordId): Rect? = frames[id]?.frame

    /**
     * The word under [point], or the nearest plausible one.
     *
     * Exact containment first. Failing that, fall back to the nearest word
     * whose vertical band contains the point, so a finger in the gap between
     * two words or just past the end of a line still lands somewhere sensible
     * instead of dropping the drag. iOS does the same, and without it the
     * selection stutters wherever the text has ragged edges.
     */
    fun wordAt(point: Offset, verticalSlop: Float = 12f): WordId? {
        frames.values.firstOrNull { it.frame.contains(point) }?.let { return it.id }

        var best: WordId? = null
        var bestDistance = Float.MAX_VALUE
        for ((id, frame, _) in frames.values) {
            val withinBand = point.y >= frame.top - verticalSlop &&
                point.y <= frame.bottom + verticalSlop
            if (!withinBand) continue
            val dx = when {
                point.x < frame.left -> frame.left - point.x
                point.x > frame.right -> point.x - frame.right
                else -> 0f
            }
            val dy = abs(point.y - frame.center.y)
            val distance = dx + dy * 0.25f
            if (distance < bestDistance) {
                bestDistance = distance
                best = id
            }
        }
        return best
    }

    fun begin(at: WordId) {
        anchor = at
        head = at
    }

    fun extendTo(id: WordId) {
        if (anchor != null) head = id
    }

    fun end() {
        // Frames stay registered; only the drag ends.
    }

    fun clear() {
        anchor = null
        head = null
    }

    /** The selected ids, in reading order, inclusive of both endpoints. */
    fun selection(): List<WordId> {
        val a = anchor ?: return emptyList()
        val h = head ?: return emptyList()
        val (lo, hi) = if (a <= h) a to h else h to a
        return frames.keys.filter { it in lo..hi }.sorted()
    }

    fun isSelected(id: WordId): Boolean {
        val a = anchor ?: return false
        val h = head ?: return false
        return if (a <= h) id in a..h else id in h..a
    }

    /**
     * The persisted shape of a selection: a verse plus a word range.
     *
     * Matches BibleHighlight.startWordIndex / endWordIndex on iOS, which is why
     * a selection spanning several verses yields one entry per verse rather
     * than a single flat range.
     */
    fun ranges(): List<VerseWordRange> = selection()
        .groupBy { it.verse }
        .map { (verse, ids) -> VerseWordRange(verse, ids.first().word, ids.last().word) }
        .sortedBy { it.verse }

    fun selectedText(): String = selection().mapNotNull { frames[it]?.text }.joinToString(" ")
}

data class VerseWordRange(val verse: Int, val startWord: Int, val endWord: Int)
