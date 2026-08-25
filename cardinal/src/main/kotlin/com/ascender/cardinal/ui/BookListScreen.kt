package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ascender.cardinal.data.BibleBook
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightLazyScrollView
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
            LightLazyScrollView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp()),
                uniformItemHeightGridUnits = ROW_HEIGHT_UNITS,
            ) {
                item { TestamentHeading("Old Testament") }
                items(BibleBook.old) { book -> BookRow(book) }
                item { TestamentHeading("New Testament") }
                items(BibleBook.new) { book -> BookRow(book) }
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
                .padding(top = 1f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
        )
    }
}
