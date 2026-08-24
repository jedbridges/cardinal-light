package com.ascender.cardinal.data

/**
 * The three translations this tool ships.
 *
 * All three carry no copyright on the text itself, which is what makes them
 * safe to bundle in an open-source tool that Light builds and signs from a
 * public commit. Nothing licensed is ever added here: a licensed translation
 * would need a network fetch and a promise never to persist the text, and
 * this tool makes no network calls at all.
 *
 * [attribution] is shown on the translation screen and in the README. The iOS
 * app returns no copyright notice for these three, because none is legally
 * required; the BSB line is a courtesy that Berean asks for.
 */
enum class Translation(
    val code: String,
    val displayName: String,
    val attribution: String,
) {
    WEB(
        code = "WEB",
        displayName = "World English Bible",
        attribution = "Public domain. No permission needed to copy, quote, or print.",
    ),
    KJV(
        code = "KJV",
        displayName = "King James Version",
        attribution = "Public domain.",
    ),
    BSB(
        code = "BSB",
        displayName = "Berean Standard Bible",
        attribution = "Dedicated to the public domain on April 30, 2023. " +
            "Courtesy of berean.bible.",
    );

    companion object {
        val default = WEB

        fun fromCode(code: String?): Translation =
            entries.firstOrNull { it.code == code } ?: default
    }
}
