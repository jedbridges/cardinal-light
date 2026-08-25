package com.ascender.cardinal.data

/**
 * The 66 books, ported from the iOS BibleBook.swift. Only the fields a reader
 * needs; the iOS type also carries author, genre and timeline data for
 * features this tool does not have.
 */
enum class Testament { OLD, NEW }

data class BibleBook(
    val id: Int,
    val name: String,
    val abbreviation: String,
    val testament: Testament,
    val chapterCount: Int,
) {
    /** Asset filename stem, e.g. "Song_of_Solomon". */
    val slug: String get() = name.replace(" ", "_")

    fun assetPath(translation: String): String = "bible/${translation}_${id}_$slug.json"

    companion object {
        fun byId(id: Int): BibleBook? = all.firstOrNull { it.id == id }

        val old: List<BibleBook> get() = all.filter { it.testament == Testament.OLD }
        val new: List<BibleBook> get() = all.filter { it.testament == Testament.NEW }

        val all: List<BibleBook> = listOf(
            BibleBook(1, "Genesis", "Gen", Testament.OLD, 50),
            BibleBook(2, "Exodus", "Exo", Testament.OLD, 40),
            BibleBook(3, "Leviticus", "Lev", Testament.OLD, 27),
            BibleBook(4, "Numbers", "Num", Testament.OLD, 36),
            BibleBook(5, "Deuteronomy", "Deut", Testament.OLD, 34),
            BibleBook(6, "Joshua", "Josh", Testament.OLD, 24),
            BibleBook(7, "Judges", "Judg", Testament.OLD, 21),
            BibleBook(8, "Ruth", "Ruth", Testament.OLD, 4),
            BibleBook(9, "1 Samuel", "1 Sam", Testament.OLD, 31),
            BibleBook(10, "2 Samuel", "2 Sam", Testament.OLD, 24),
            BibleBook(11, "1 Kings", "1 Ki", Testament.OLD, 22),
            BibleBook(12, "2 Kings", "2 Ki", Testament.OLD, 25),
            BibleBook(13, "1 Chronicles", "1 Chr", Testament.OLD, 29),
            BibleBook(14, "2 Chronicles", "2 Chr", Testament.OLD, 36),
            BibleBook(15, "Ezra", "Ezra", Testament.OLD, 10),
            BibleBook(16, "Nehemiah", "Neh", Testament.OLD, 13),
            BibleBook(17, "Esther", "Est", Testament.OLD, 10),
            BibleBook(18, "Job", "Job", Testament.OLD, 42),
            BibleBook(19, "Psalms", "Ps", Testament.OLD, 150),
            BibleBook(20, "Proverbs", "Prov", Testament.OLD, 31),
            BibleBook(21, "Ecclesiastes", "Eccl", Testament.OLD, 12),
            BibleBook(22, "Song of Solomon", "Song", Testament.OLD, 8),
            BibleBook(23, "Isaiah", "Isa", Testament.OLD, 66),
            BibleBook(24, "Jeremiah", "Jer", Testament.OLD, 52),
            BibleBook(25, "Lamentations", "Lam", Testament.OLD, 5),
            BibleBook(26, "Ezekiel", "Ezek", Testament.OLD, 48),
            BibleBook(27, "Daniel", "Dan", Testament.OLD, 12),
            BibleBook(28, "Hosea", "Hos", Testament.OLD, 14),
            BibleBook(29, "Joel", "Joel", Testament.OLD, 3),
            BibleBook(30, "Amos", "Amos", Testament.OLD, 9),
            BibleBook(31, "Obadiah", "Obad", Testament.OLD, 1),
            BibleBook(32, "Jonah", "Jon", Testament.OLD, 4),
            BibleBook(33, "Micah", "Mic", Testament.OLD, 7),
            BibleBook(34, "Nahum", "Nah", Testament.OLD, 3),
            BibleBook(35, "Habakkuk", "Hab", Testament.OLD, 3),
            BibleBook(36, "Zephaniah", "Zeph", Testament.OLD, 3),
            BibleBook(37, "Haggai", "Hag", Testament.OLD, 2),
            BibleBook(38, "Zechariah", "Zech", Testament.OLD, 14),
            BibleBook(39, "Malachi", "Mal", Testament.OLD, 4),
            BibleBook(40, "Matthew", "Matt", Testament.NEW, 28),
            BibleBook(41, "Mark", "Mark", Testament.NEW, 16),
            BibleBook(42, "Luke", "Luke", Testament.NEW, 24),
            BibleBook(43, "John", "John", Testament.NEW, 21),
            BibleBook(44, "Acts", "Acts", Testament.NEW, 28),
            BibleBook(45, "Romans", "Rom", Testament.NEW, 16),
            BibleBook(46, "1 Corinthians", "1 Cor", Testament.NEW, 16),
            BibleBook(47, "2 Corinthians", "2 Cor", Testament.NEW, 13),
            BibleBook(48, "Galatians", "Gal", Testament.NEW, 6),
            BibleBook(49, "Ephesians", "Eph", Testament.NEW, 6),
            BibleBook(50, "Philippians", "Phil", Testament.NEW, 4),
            BibleBook(51, "Colossians", "Col", Testament.NEW, 4),
            BibleBook(52, "1 Thessalonians", "1 Th", Testament.NEW, 5),
            BibleBook(53, "2 Thessalonians", "2 Th", Testament.NEW, 3),
            BibleBook(54, "1 Timothy", "1 Tim", Testament.NEW, 6),
            BibleBook(55, "2 Timothy", "2 Tim", Testament.NEW, 4),
            BibleBook(56, "Titus", "Tit", Testament.NEW, 3),
            BibleBook(57, "Philemon", "Phm", Testament.NEW, 1),
            BibleBook(58, "Hebrews", "Heb", Testament.NEW, 13),
            BibleBook(59, "James", "Jas", Testament.NEW, 5),
            BibleBook(60, "1 Peter", "1 Pet", Testament.NEW, 5),
            BibleBook(61, "2 Peter", "2 Pet", Testament.NEW, 3),
            BibleBook(62, "1 John", "1 Jn", Testament.NEW, 5),
            BibleBook(63, "2 John", "2 Jn", Testament.NEW, 1),
            BibleBook(64, "3 John", "3 Jn", Testament.NEW, 1),
            BibleBook(65, "Jude", "Jude", Testament.NEW, 1),
            BibleBook(66, "Revelation", "Rev", Testament.NEW, 22),
        )
    }
}

/** "John 1" — a chapter. */
fun chapterReference(book: Int, chapter: Int): String =
    "${BibleBook.byId(book)?.name ?: "?"} $chapter"

/** "John 1:1" — a verse. The one place this string is built. */
fun verseReference(book: Int, chapter: Int, verse: Int): String =
    "${chapterReference(book, chapter)}:$verse"
