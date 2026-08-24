package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.ascender.cardinal.data.BibleRepository
import com.ascender.cardinal.data.Highlight
import com.ascender.cardinal.data.Reference
import com.ascender.cardinal.data.ReaderStore
import com.ascender.cardinal.data.Translation
import com.ascender.cardinal.data.VerseRecord
import com.ascender.cardinal.reader.VerseWordRange
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderUiState(
    val reference: Reference,
    val translation: Translation = Translation.default,
    val verses: List<VerseRecord> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val loading: Boolean = true,
    val previous: Reference? = null,
    val next: Reference? = null,
) {
    val title: String get() = reference.display
    val hasPrevious: Boolean get() = previous != null
    val hasNext: Boolean get() = next != null

    /** Naming the next chapter is the difference between a button and an invitation. */
    val nextLabel: String get() = next?.display ?: "Next"
}

class ReaderViewModel(
    private val store: ReaderStore,
    private val repository: BibleRepository,
    initial: Reference,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(ReaderUiState(reference = initial))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Translation comes from stored state; the reference comes from
            // whoever opened this screen, which may not be where we left off.
            open(store.state.first().currentTranslation, initial)
        }
        viewModelScope.launch {
            store.state.collect { stored ->
                _state.value = _state.value.copy(
                    highlights = stored.highlights.filter {
                        it.book == _state.value.reference.bookId &&
                            it.chapter == _state.value.reference.chapter
                    },
                )
            }
        }
    }

    private suspend fun open(translation: Translation, reference: Reference) {
        _state.value = _state.value.copy(
            reference = reference,
            translation = translation,
            loading = true,
        )
        val verses = repository.chapter(translation, reference.bookId, reference.chapter)
        val stored = store.state.first()
        _state.value = ReaderUiState(
            reference = reference,
            translation = translation,
            verses = verses,
            highlights = stored.highlights.filter {
                it.book == reference.bookId && it.chapter == reference.chapter
            },
            loading = false,
            previous = repository.previousChapter(reference.bookId, reference.chapter),
            next = repository.nextChapter(reference.bookId, reference.chapter),
        )
        store.setPosition(reference.bookId, reference.chapter)
    }

    /**
     * Paging happens in place rather than by pushing another screen, so reading
     * Genesis end to end leaves one entry on the back stack instead of fifty,
     * and the book stays in the repository's cache the whole way through.
     */
    fun goToNext() = move { repository.nextChapter(it.bookId, it.chapter) }

    fun goToPrevious() = move { repository.previousChapter(it.bookId, it.chapter) }

    private fun move(next: (Reference) -> Reference?) {
        val target = next(_state.value.reference) ?: return
        viewModelScope.launch { open(_state.value.translation, target) }
    }

    fun toggleVerse(verse: Int) {
        val reference = _state.value.reference
        viewModelScope.launch {
            store.toggleVerse(
                _state.value.translation,
                reference.bookId,
                reference.chapter,
                verse,
            )
        }
    }

    fun commitSelection(ranges: List<VerseWordRange>) {
        val reference = _state.value.reference
        viewModelScope.launch {
            store.setWordRanges(
                _state.value.translation,
                reference.bookId,
                reference.chapter,
                ranges,
            )
        }
    }
}

/**
 * The chapter itself. Tap a verse to highlight it, long-press and drag to
 * highlight words.
 */
class ReaderScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: Int,
    private val chapter: Int,
) : LightScreen<Unit, ReaderViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReaderViewModel> get() = ReaderViewModel::class.java

    override fun createViewModel() = ReaderViewModel(
        store = ReaderStore(lightContext.dataStore),
        repository = BibleRepository(readAsset = { lightContext.readAsset(it) }),
        initial = Reference(bookId, chapter),
    )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()

        CardinalScreen(
            title = state.title,
            subtitle = state.translation.code,
            onBack = { goBack() },
        ) {
            when {
                state.loading -> Placeholder("...")
                state.verses.isEmpty() -> Placeholder("Nothing here.")
                else -> ChapterText(
                    verses = state.verses,
                    highlights = state.highlights,
                    modifier = Modifier.fillMaxSize(),
                    onVerseTap = viewModel::toggleVerse,
                    onSelectionChanged = viewModel::commitSelection,
                    footer = { ChapterFooter(state) },
                )
            }
        }
    }

    /**
     * Paging lives at the end of the text, not in a persistent bar. The screen
     * is 31 grid units tall and a bar would spend four of them telling you
     * about a chapter you have not finished yet.
     */
    @Composable
    private fun ChapterFooter(state: ReaderUiState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.5f.gridUnitsAsDp(), bottom = 2f.gridUnitsAsDp()),
        ) {
            if (state.hasPrevious) {
                CardinalRow(text = "Previous", onClick = viewModel::goToPrevious)
            }
            if (state.hasNext) {
                CardinalRow(text = state.nextLabel, onClick = viewModel::goToNext)
            }
        }
    }

    @Composable
    private fun Placeholder(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            lighten = true,
            modifier = Modifier.padding(
                horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                vertical = 1f.gridUnitsAsDp(),
            ),
        )
    }
}
