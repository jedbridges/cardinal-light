package com.ascender.cardinal.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The offered order and the default are product decisions, not incidental to
 * how the enum happens to be written. Pinned so a reorder is deliberate.
 */
class TranslationOrderTest {

    @Test fun `King James is the default`() {
        assertEquals(Translation.KJV, Translation.default)
        assertEquals("KJV", ReaderState().translation, "a fresh install opens in KJV")
    }

    @Test fun `King James is offered first`() {
        assertEquals(
            listOf("KJV", "WEB", "BSB"),
            Translation.entries.map { it.code },
        )
    }

    @Test fun `an unknown stored code falls back to the default`() {
        assertEquals(Translation.KJV, Translation.fromCode("NIV"))
        assertEquals(Translation.KJV, Translation.fromCode(null))
        assertEquals(Translation.WEB, Translation.fromCode("WEB"))
    }
}
