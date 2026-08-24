#!/usr/bin/env python3
"""
Regenerate the bundled Bible assets from public-domain sources.

The 198 files under cardinal/src/main/assets/bible are checked in so the tool
builds offline and so Light's build server, which only fetches your git commit,
has everything it needs. This script is what makes those files auditable rather
than an opaque 16 MB blob: run it and you should get the same bytes back.

Public domain only. KJV, WEB and the Berean Standard Bible, which was dedicated
to the public domain on 30 April 2023 (berean.bible/licensing.htm). A
copyrighted translation must never be added here: it could not be redistributed
in an open-source repo, and this tool has no network path to stream one.

Usage, from the repo root:
    python3 scripts/build_bible_assets.py            # all three
    python3 scripts/build_bible_assets.py WEB        # just one

Output: cardinal/src/main/assets/bible/{CODE}_{id}_{Book_Name}.json
Format: [{"bookId": N, "chapter": N, "verse": N, "text": "..."}, ...]

Adapted from the iOS repo's scripts/download_pd_bible.py. The HTML stripping
below is a mirror of BibleDownloadManager.stripHTML and must stay that way, so
the same verse reads identically on both platforms.

Resumable: existing book files are skipped. Delete a file to refetch it.
Rate limited to one request per THROTTLE seconds; a full translation is 1189
requests, so budget roughly ten minutes each.
"""

import argparse
import html
import json
import os
import re
import ssl
import sys
import time
import urllib.request

_ssl_ctx = ssl.create_default_context()

BASE_URL = "https://bolls.life"
THROTTLE = 0.25  # seconds between request starts
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS_DIR = os.path.join(REPO_ROOT, "cardinal", "src", "main", "assets", "bible")

# Only these. Adding a code here is a licensing decision, not a config change.
PUBLIC_DOMAIN_CODES = ["WEB", "KJV", "BSB"]

BOOKS = [
    (1,  "Genesis",          50),
    (2,  "Exodus",           40),
    (3,  "Leviticus",        27),
    (4,  "Numbers",          36),
    (5,  "Deuteronomy",      34),
    (6,  "Joshua",           24),
    (7,  "Judges",           21),
    (8,  "Ruth",              4),
    (9,  "1 Samuel",         31),
    (10, "2 Samuel",         24),
    (11, "1 Kings",          22),
    (12, "2 Kings",          25),
    (13, "1 Chronicles",     29),
    (14, "2 Chronicles",     36),
    (15, "Ezra",             10),
    (16, "Nehemiah",         13),
    (17, "Esther",           10),
    (18, "Job",              42),
    (19, "Psalms",          150),
    (20, "Proverbs",         31),
    (21, "Ecclesiastes",     12),
    (22, "Song of Solomon",   8),
    (23, "Isaiah",           66),
    (24, "Jeremiah",         52),
    (25, "Lamentations",      5),
    (26, "Ezekiel",          48),
    (27, "Daniel",           12),
    (28, "Hosea",            14),
    (29, "Joel",              3),
    (30, "Amos",              9),
    (31, "Obadiah",           1),
    (32, "Jonah",             4),
    (33, "Micah",             7),
    (34, "Nahum",             3),
    (35, "Habakkuk",          3),
    (36, "Zephaniah",         3),
    (37, "Haggai",            2),
    (38, "Zechariah",        14),
    (39, "Malachi",           4),
    (40, "Matthew",          28),
    (41, "Mark",             16),
    (42, "Luke",             24),
    (43, "John",             21),
    (44, "Acts",             28),
    (45, "Romans",           16),
    (46, "1 Corinthians",    16),
    (47, "2 Corinthians",    13),
    (48, "Galatians",         6),
    (49, "Ephesians",         6),
    (50, "Philippians",       4),
    (51, "Colossians",        4),
    (52, "1 Thessalonians",   5),
    (53, "2 Thessalonians",   3),
    (54, "1 Timothy",         6),
    (55, "2 Timothy",         4),
    (56, "Titus",             3),
    (57, "Philemon",          1),
    (58, "Hebrews",          13),
    (59, "James",             5),
    (60, "1 Peter",           5),
    (61, "2 Peter",           3),
    (62, "1 John",            5),
    (63, "2 John",            1),
    (64, "3 John",            1),
    (65, "Jude",              1),
    (66, "Revelation",       22),
]


# Mirrors of BibleDownloadManager.stripHTML. Changing any of these changes the
# bundled text, so they must stay in step with the iOS regexes.
_STRONGS = re.compile(r"<S>\d+</S>")
_SUP = re.compile(r"<sup\b[^>]*>.*?</sup>", re.IGNORECASE | re.DOTALL)
_TAG = re.compile(r"<[^>]+>")
_WS = re.compile(r"\s+")
_SPACE_PUNCT = re.compile(r"\s+([,.;:!?])")


def strip_html(text):
    """Mirror of BibleDownloadManager.stripHTML (Swift)."""
    clean = _STRONGS.sub(" ", text)
    # Drop footnote elements INCLUDING their inner text, so footnote wording
    # never leaks into the verse (e.g. KJV Ps 23:2 "green: Heb. pastures...").
    clean = _SUP.sub("", clean)
    clean = _TAG.sub(" ", clean)
    clean = html.unescape(clean)
    clean = _WS.sub(" ", clean)
    clean = _SPACE_PUNCT.sub(r"\1", clean)
    return clean.strip()


_last_request = [0.0]



def fetch_chapter(api_code, book_id, chapter, retries=5):
    """Fetch one chapter, throttled and with backoff. Returns raw verse dicts."""
    url = f"{BASE_URL}/get-text/{api_code}/{book_id}/{chapter}/"
    delay = 0.5
    for attempt in range(retries):
        wait = THROTTLE - (time.monotonic() - _last_request[0])
        if wait > 0:
            time.sleep(wait)
        _last_request[0] = time.monotonic()
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Cardinal/1.0"})
            with urllib.request.urlopen(req, timeout=30, context=_ssl_ctx) as resp:
                if resp.getcode() == 429:
                    time.sleep(delay * 4)
                    delay = min(delay * 2, 16)
                    continue
                data = json.loads(resp.read().decode("utf-8"))
                return data if isinstance(data, list) else []
        except Exception as e:  # noqa: BLE001 - retry any transient failure
            if attempt < retries - 1:
                time.sleep(delay)
                delay = min(delay * 2, 8)
            else:
                print(f"  ERROR: book={book_id} ch={chapter}: {e}", file=sys.stderr)
                return []
    return []



def download_book(api_code, book_id, chapter_count):
    all_verses = []
    for ch in range(1, chapter_count + 1):
        for item in fetch_chapter(api_code, book_id, ch):
            if "verse" in item and "text" in item:
                text = strip_html(item["text"])
                if text:
                    all_verses.append(
                        {"bookId": book_id, "chapter": ch, "verse": item["verse"], "text": text}
                    )
    return all_verses



def book_filename(code, book_id, book_name):
    """Matches BibleBook.assetPath() in the Kotlin source. Keep them in step."""
    return f"{code}_{book_id}_{book_name.replace(' ', '_')}.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "codes", nargs="*", default=PUBLIC_DOMAIN_CODES,
        help="translation codes to build (default: all three)",
    )
    parser.add_argument("--force", action="store_true", help="refetch existing files")
    args = parser.parse_args()

    for code in args.codes:
        if code not in PUBLIC_DOMAIN_CODES:
            sys.exit(f"{code} is not in the public-domain allowlist: {PUBLIC_DOMAIN_CODES}")

    os.makedirs(ASSETS_DIR, exist_ok=True)

    for code in args.codes:
        print(f"== {code} ==")
        for book_id, book_name, chapter_count in BOOKS:
            path = os.path.join(ASSETS_DIR, book_filename(code, book_id, book_name))
            if os.path.exists(path) and not args.force:
                continue
            verses = download_book(code, book_id, chapter_count)
            with open(path, "w", encoding="utf-8") as handle:
                json.dump(verses, handle, ensure_ascii=False)
            print(f"  {book_name}: {len(verses)} verses")

    print("done")


if __name__ == "__main__":
    main()
