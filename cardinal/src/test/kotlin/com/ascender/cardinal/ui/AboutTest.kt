package com.ascender.cardinal.ui

import com.ascender.cardinal.data.Translation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The About screen states a version and a source URL. Both are the kind of
 * thing that quietly goes stale, and both are what a reviewer would check.
 */
class AboutTest {

    private val manifest = File("lighttool.toml").readText()

    @Test fun `the version on screen matches lighttool_toml`() {
        val declared = Regex("""versionName\s*=\s*"([^"]+)"""")
            .find(manifest)?.groupValues?.get(1)
        assertEquals(declared, CARDINAL_VERSION, "About and lighttool.toml disagree")
    }

    @Test fun `the source URL points at the repo Light builds from`() {
        assertEquals("github.com/jedbridges/cardinal-light", CARDINAL_SOURCE)
        assertTrue(!CARDINAL_SOURCE.startsWith("http"), "shown as text, not a link")
    }

    @Test fun `every shipped translation carries an attribution`() {
        Translation.entries.forEach {
            assertTrue(it.attribution.isNotBlank(), "${it.code} has no attribution")
            assertTrue(it.attribution.trimEnd().endsWith("."), "${it.code}: not a sentence")
        }
    }
}
