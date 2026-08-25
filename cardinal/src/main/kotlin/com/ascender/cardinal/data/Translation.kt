package com.ascender.cardinal.data

/**
 * The three translations this tool ships, in the order they are offered.
 * Declaration order drives both the picker and the About list.
 *
 * All carry no copyright on the text,
 * which is what makes them safe to bundle in an open-source repo that Light
 * builds from a public commit.
 *
 * Never add a licensed translation: it would need a network fetch and a
 * promise never to persist the text, and this tool has neither.
 */
enum class Translation(
    val code: String,
    val displayName: String,
    val attribution: String,
) {
    KJV(
        code = "KJV",
        displayName = "King James Version",
        attribution = "Public domain.",
    ),
    WEB(
        code = "WEB",
        displayName = "World English Bible",
        attribution = "Public domain. No permission needed to copy, quote, or print.",
    ),
    BSB(
        code = "BSB",
        displayName = "Berean Standard Bible",
        attribution = "Dedicated to the public domain on April 30, 2023. " +
            "Courtesy of berean.bible.",
    );

    companion object {
        val default = KJV

        fun fromCode(code: String?): Translation =
            entries.firstOrNull { it.code == code } ?: default
    }
}
