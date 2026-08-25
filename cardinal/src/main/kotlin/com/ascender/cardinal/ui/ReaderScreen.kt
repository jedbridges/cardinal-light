package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
import androidx.compose.material3.HorizontalDivider
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
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

    val undo = UndoController(viewModelScope, store)

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
        undo.dismiss()
        viewModelScope.launch { open(_state.value.translation, target) }
    }

    fun toggleVerse(verse: Int) {
        val reference = _state.value.reference
        viewModelScope.launch {
            val removed = store.toggleVerse(
                _state.value.translation,
                reference.bookId,
                reference.chapter,
                verse,
            )
            undo.offer("Removed ${reference.display}:$verse", removed)
        }
    }

    fun commitSelection(ranges: List<VerseWordRange>) {
        val reference = _state.value.reference
        viewModelScope.launch {
            val replaced = store.setWordRanges(
                _state.value.translation,
                reference.bookId,
                reference.chapter,
                ranges,
            )
            undo.offer(replacedMessage(_state.value.reference, replaced), replaced)
        }
    }

}


/**
 * "Replaced John 1:1" beats "Highlight replaced": the reader is looking at a
 * wall of verses and needs to know which one the message is about.
 *
 * Total on purpose. The first version indexed into the verse list without
 * checking it was populated, and a drag that replaces nothing — which is most
 * drags — took the whole app down with it.
 */
fun replacedMessage(reference: Reference, replaced: List<Highlight>): String {
    val verses = replaced.map { it.verse }.distinct().sorted()
    return when {
        verses.isEmpty() -> ""
        verses.size == 1 -> "Replaced ${reference.display}:${verses.first()}"
        else -> "Replaced ${reference.display}:${verses.first()}-${verses.last()}"
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
        val pendingUndo by viewModel.undo.pending.collectAsState()

        CardinalScreen(
            title = state.title,
            subtitle = state.translation.code,
            onBack = { goBack() },
            overlay = {
                pendingUndo?.let {
                    UndoRow(
                        pending = it,
                        onUndo = viewModel.undo::undo,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            },
        ) {
            when {
                state.loading -> Placeholder("Opening ${state.reference.display}")
                // Only reachable if a bundled asset is missing or unreadable,
                // which is a packaging fault rather than anything the reader
                // did. Say so plainly instead of "Nothing here."
                state.verses.isEmpty() ->
                    Placeholder("${state.reference.display} could not be opened.")
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
     *
     * Two things here were wrong and are worth naming. The rows used to render
     * at Copy, which is 30 design px against scripture's 25 — the controls were
     * a fifth larger than the words they were interrupting, in an app whose
     * first principle is that the text is primary. And the labels were
     * asymmetric, a generic "Previous" stacked above a named "John 2", which
     * scans top-to-bottom as though John 2 were the chapter behind you.
     *
     * Both chapters are named now, both sit below scripture in the type scale,
     * and a rule separates the navigation from the last verse.
     */
    @Composable
    private fun ChapterFooter(state: ReaderUiState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.section.gridUnitsAsDp(), bottom = Space.section.gridUnitsAsDp()),
        ) {
            HorizontalDivider(
                color = LightThemeTokens.colors.contentSecondary,
                thickness = Dp.Hairline,
                modifier = Modifier.padding(bottom = Space.base.gridUnitsAsDp()),
            )
            state.previous?.let { previous ->
                CardinalRow(
                    text = previous.display,
                    detail = "Previous",
                    variant = LightTextVariant.Detail,
                    onClickLabel = "Previous chapter, ${previous.display}",
                    onClick = viewModel::goToPrevious,
                )
            }
            state.next?.let { next ->
                CardinalRow(
                    text = next.display,
                    detail = "Next",
                    variant = LightTextVariant.Detail,
                    onClickLabel = "Next chapter, ${next.display}",
                    onClick = viewModel::goToNext,
                )
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
                vertical = Space.base.gridUnitsAsDp(),
            ),
        )
    }
}
