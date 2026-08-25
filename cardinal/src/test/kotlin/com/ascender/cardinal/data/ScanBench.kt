package com.ascender.cardinal.data

import java.io.File
import kotlin.test.Test

class ScanBench {
    @Test fun bench() {
        val assets = File("src/main/assets")
        val raws = BibleBook.all.map { File(assets, it.assetPath("WEB")).readBytes() }
        val needle = "shepherd"

        fun time(label: String, block: () -> Int) {
            repeat(2) { block() }                       // warm
            val t = System.nanoTime()
            val n = block()
            println("  %-46s %6.0f ms  (hits in %d books)".format(
                label, (System.nanoTime() - t) / 1e6, n))
        }

        time("decodeToString only") {
            var n = 0; raws.forEach { if (it.decodeToString().isNotEmpty()) n++ }; n
        }
        time("decode + contains(ignoreCase = true)   <- current") {
            var n = 0
            raws.forEach { if (it.decodeToString().contains(needle, ignoreCase = true)) n++ }
            n
        }
        time("decode + lowercase + contains(exact)") {
            val lower = needle.lowercase()
            var n = 0
            raws.forEach { if (it.decodeToString().lowercase().contains(lower)) n++ }
            n
        }
        time("decode + contains(exact, already-lower needle)") {
            var n = 0
            raws.forEach { if (it.decodeToString().contains(needle)) n++ }
            n
        }
    }
}
