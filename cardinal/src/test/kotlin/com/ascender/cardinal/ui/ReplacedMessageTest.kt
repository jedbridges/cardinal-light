package com.ascender.cardinal.ui

import com.ascender.cardinal.data.Highlight
import com.ascender.cardinal.data.Reference
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The undo message for a word drag that displaced existing marks.
 *
 * The empty case is the reason this test exists. The first version of this
 * function indexed into the verse list without checking it had anything in it,
 * so a drag over unmarked text — which is most drags — crashed the app on
 * release of the finger. It reached a device before it was caught, which is
 * the argument for pinning it here.
 */
class ReplacedMessageTest {

    private val john1 = Reference(bookId = 43, chapter = 1)

    private fun mark(verse: Int) =
        Highlight(book = 43, chapter = 1, verse = verse, translation = "WEB")

    @Test fun `replacing nothing yields no message rather than an exception`() {
        assertEquals("", replacedMessage(john1, emptyList()))
    }

    @Test fun `one verse is named exactly`() {
        assertEquals("Replaced John 1:3", replacedMessage(john1, listOf(mark(3))))
    }

    @Test fun `several verses collapse to a range`() {
        assertEquals(
            "Replaced John 1:3-7",
            replacedMessage(john1, listOf(mark(3), mark(5), mark(7))),
        )
    }

    @Test fun `verses out of order still read low to high`() {
        assertEquals(
            "Replaced John 1:2-9",
            replacedMessage(john1, listOf(mark(9), mark(2), mark(5))),
        )
    }

    @Test fun `duplicate marks on one verse do not become a range`() {
        val partial = Highlight(43, 1, 4, startWord = 0, endWord = 2, translation = "WEB")
        val other = Highlight(43, 1, 4, startWord = 5, endWord = 6, translation = "WEB")
        assertEquals("Replaced John 1:4", replacedMessage(john1, listOf(partial, other)))
    }
}
