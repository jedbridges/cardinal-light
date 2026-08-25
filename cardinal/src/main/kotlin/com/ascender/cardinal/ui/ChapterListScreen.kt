package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ascender.cardinal.data.BibleBook
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** Chapter numbers for one book. Short labels, so a grid rather than a list. */
class ChapterListScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: Int,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val book = BibleBook.byId(bookId)

        CardinalScreen(title = book?.name ?: "Chapters", onBack = { goBack() }) {
            if (book == null) return@CardinalScreen

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp()),
                verticalArrangement = Arrangement.spacedBy(Space.tight.gridUnitsAsDp()),
                horizontalArrangement = Arrangement.spacedBy(Space.tight.gridUnitsAsDp()),
            ) {
                items((1..book.chapterCount).toList()) { chapter ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CELL_HEIGHT_UNITS.gridUnitsAsDp())
                            .lightClickable(
                                onClickLabel = "Chapter $chapter",
                            ) { openChapter(chapter) },
                        contentAlignment = Alignment.Center,
                    ) {
                        LightText(text = "$chapter", variant = LightTextVariant.Copy)
                    }
                }
            }
        }
    }

    private fun openChapter(chapter: Int) {
        navigateTo({ ReaderScreen(it, bookId, chapter) })
    }

    private companion object {
        const val COLUMNS = 5

        /** 45.7dp on the LP3, above the 44dp minimum touch target. */
        const val CELL_HEIGHT_UNITS = 3f
    }
}
