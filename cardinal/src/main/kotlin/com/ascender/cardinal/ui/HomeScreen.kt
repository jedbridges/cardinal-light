package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.ascender.cardinal.data.BibleBook
import com.ascender.cardinal.data.ReaderState
import com.ascender.cardinal.data.ReaderStore
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(store: ReaderStore) : LightViewModel<Unit>() {
    val state: StateFlow<ReaderState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())
}

/**
 * The landing screen: where you left off, then the three ways in.
 *
 * Continue-reading is the first thing and the largest thing, because on a phone
 * bought to be boring the common case is picking up mid-book, not browsing.
 */
@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel> get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(ReaderStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val book = BibleBook.byId(state.lastBook)

        CardinalScreen(title = "Cardinal") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp()),
            ) {
                LightText(
                    text = "Continue",
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    modifier = Modifier.padding(top = Space.base.gridUnitsAsDp()),
                )
                CardinalRow(
                    text = "${book?.name ?: "John"} ${state.lastChapter}",
                    detail = state.currentTranslation.code,
                    variant = LightTextVariant.Heading,
                    onClick = { openReader(state.lastBook, state.lastChapter) },
                )

                // Where you left off is a different kind of thing from a list
                // of places to go. Without a break it sat on the same rhythm as
                // the rows below and read as the first of five destinations.
                Spacer(modifier = Modifier.height(Space.section.gridUnitsAsDp()))

                CardinalRow(text = "Books", onClick = { navigateTo(::BookListScreen) })
                CardinalRow(text = "Search", onClick = { navigateTo(::SearchScreen) })
                CardinalRow(
                    text = "Highlights",
                    detail = state.highlights.size.takeIf { it > 0 }
                        ?.let { if (it == 1) "1 verse" else "$it verses" },
                    onClick = { navigateTo(::HighlightsScreen) },
                )
                CardinalRow(
                    text = "Translation",
                    detail = state.currentTranslation.displayName,
                    onClick = { navigateTo(::TranslationScreen) },
                )
            }
        }
    }

    private fun openReader(bookId: Int, chapter: Int) {
        navigateTo({ ReaderScreen(it, bookId, chapter) })
    }
}
