# Cardinal

A Bible reader for the Light Phone III. Pick a book, pick a chapter, read.
Tap a verse to highlight it. It remembers where you were.

That is the whole app. There is no AI, no memorization, no reading plan, no
account, no sync, no streaks, and no network call of any kind. Every verse of
all three translations ships inside the APK.

Cardinal is the reading half of [the iOS app of the same
name](https://apps.apple.com/app/cardinal), rebuilt from scratch for LightOS
rather than ported. The iOS app is SwiftUI; this is Kotlin and Compose against
the official [Light SDK](https://github.com/lightphone/light-sdk), and it
deliberately keeps only the part that belongs on a phone you bought in order to
use it less.

## Translations

| Code | Translation | Status |
| --- | --- | --- |
| WEB | World English Bible | Public domain. No permission needed to copy, quote, or print. |
| KJV | King James Version | Public domain. |
| BSB | Berean Standard Bible | Dedicated to the public domain on 30 April 2023. Courtesy of [berean.bible](https://berean.bible/licensing.htm). |

Nothing copyrighted will ever be added. A licensed translation could not be
redistributed in an open-source repo that Light builds from a public commit,
and there is no network path here to stream one.

## Building

You need Android Studio (for its bundled JDK and the Android SDK). From the
repo root:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :cardinal:assembleDebug
```

Tests are plain JVM tests and take a couple of seconds:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :cardinal:testDebugUnitTest
```

To run it, you want an Android emulator with the LightOS emulator installed as
a system app. That setup lives in [docs/system_app](docs/system_app), and it is
the fiddliest part of working on this. Note that it asks you to fetch AOSP
platform signing keys from a third-party mirror; read that step before running
it.

The Light SDK's own README, which explains the module layout and the API
restrictions, is preserved at [docs/LIGHT_SDK_README.md](docs/LIGHT_SDK_README.md).

## The corpus

`cardinal/src/main/assets/bible` holds 198 files: 66 books times 3
translations, about 16 MB. They are checked in so the tool builds offline and
so Light's build server, which only fetches your git commit, has everything it
needs.

They are not an opaque blob. `scripts/build_bible_assets.py` regenerates them
from public-domain sources and reproduces the checked-in files byte for byte:

```bash
python3 scripts/build_bible_assets.py
```

## Shape of the code

Everything lives in `cardinal/`.

| Path | What |
| --- | --- |
| `data/BibleBook.kt` | The 66 books, ported from the iOS `BibleBook.swift`. |
| `data/BibleRepository.kt` | Reads chapters out of the assets, with a 3-book LRU. |
| `data/ReaderStore.kt` | Reading position and highlights, in the SDK's DataStore. |
| `reader/WordSelection.kt` | Word-level selection maths. Copied unchanged from the Android port. |
| `ui/ChapterText.kt` | The reader itself: tap to highlight, long-press to select words. |
| `ui/*Screen.kt` | One file per screen, each a `LightScreen` plus its ViewModel. |

### Three decisions worth knowing about

**No database.** The SDK blocks `android.content.Context`, so raw SQLite is
unreachable, and the only way to build a Room database is `buildDatabase`,
which exposes no hooks for a prepackaged asset and no migration callbacks at
all. Reading a book file directly sidesteps that: the largest of the 198 files
is 331 KB and decoding one takes a few milliseconds off the main thread.
Highlights are small and always read whole, so they go in DataStore as JSON
rather than into a schema that could never be migrated.

**No lazy list in the reader.** `LightLazyScrollView` needs a uniform item
height to size its scrollbar, and verses run from two words to a paragraph.
Composing the whole chapter also means word frames stay registered for verses
that are off-screen, which is exactly what lets a selection drag past the
bottom edge. A chapter is bounded; the worst case in the Bible is Psalm 119.

**Highlights are underlines, not blocks.** The LightOS theme is three colours:
background, content, and a secondary grey. There is no accent, so a highlight
cannot be a yellow wash. A live drag inverts the words, which is unmissable and
correctly reads as transient. A committed highlight underlines, because on a
monochrome panel an inverted block that never goes away fights the surrounding
text for the eye.

### About permissions

`cardinal/lighttool.toml` declares no permissions. The packaged APK still ends
up with `INTERNET`, `CAMERA` and a few others, because those come from library
manifests inside `:sdk:client` and `:sdk:ui` (the QR scanner, media3, DataStore,
Ktor) and merge in whether a tool uses them or not. Cardinal instantiates no
HTTP client and opens no camera.

## Licence

MIT, matching the Light SDK this repo is forked from. The Bible text is public
domain and carries no licence of its own.
