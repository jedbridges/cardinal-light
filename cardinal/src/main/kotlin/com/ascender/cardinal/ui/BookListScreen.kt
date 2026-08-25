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
 * All 66 books in canonical order. A flat list, not the grid the iOS app
 * uses: "1 Thessalonians" will not fit a third of 27 grid units.
 */
class BookListScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        CardinalScreen(title = "Books", onBack = { goBack() }) {
            // Not LightLazyScrollView: its scrollbar trusts a uniform item
            // height, which drifts whenever row padding or variant changes.
            // This one measures real content. Sixty-six rows cost nothing.
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

    /** No chapter count: it halved the books on screen, and the next screen shows them. */
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
