package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ascender.cardinal.data.BibleBook
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * All 66 books in canonical order, with a testament heading between the two.
 *
 * A flat list beats the grid the iOS app uses: the LP3 is 27 grid units wide,
 * and "1 Thessalonians" does not fit in a third of that without truncating to
 * something you have to decode.
 */
class BookListScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        CardinalScreen(title = "Books", onBack = { goBack() }) {
            // Not LightLazyScrollView. That one sizes its scrollbar from a
            // uniform item height you promise it, and the promise here was
            // 2.6 grid units against an actual row pitch of 2.88 — a tenth
            // out, which is why the thumb read as too large. Any change to row
            // padding or text variant would desynchronise it again. The plain
            // scroll view measures real content and cannot drift; sixty-six
            // short rows cost nothing to compose.
            LightScrollView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp()),
            ) {
                TestamentHeading("Old Testament")
                BibleBook.old.forEach { BookRow(it) }
                TestamentHeading("New Testament")
                BibleBook.new.forEach { BookRow(it) }
            }
        }
    }

    /**
     * No chapter count under the name. It doubled the row height, which put
     * only seven of the sixty-six books on screen, and the next screen shows
     * the chapters anyway.
     */
    @Composable
    private fun BookRow(book: BibleBook) {
        CardinalRow(
            text = book.name,
            onClickLabel = "Open ${book.name}",
            onClick = { navigateTo({ ChapterListScreen(it, book.id) }) },
        )
    }

    @Composable
    private fun TestamentHeading(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.comfy.gridUnitsAsDp(), bottom = Space.hairline.gridUnitsAsDp()),
        )
    }
}
