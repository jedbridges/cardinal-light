package com.ascender.cardinal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.thelightphone.sdk.ui.lightClickable
import com.ascender.cardinal.R
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
    val openAtVerse: Int? = null,
) {
    val title: String get() = reference.display

}

class ReaderViewModel(
    private val store: ReaderStore,
    private val repository: BibleRepository,
    private val initial: Reference,
    private val openAtVerse: Int? = null,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(
        ReaderUiState(reference = initial, openAtVerse = openAtVerse),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val undo = UndoController(viewModelScope, store)

    init {
        viewModelScope.launch {

            open(store.state.first().currentTranslation, initial, openAtVerse)
        }
        viewModelScope.launch {
            store.state.collect { stored ->
                val reference = _state.value.reference
                _state.value = _state.value.copy(
                    highlights = stored.highlightsIn(
                        reference.bookId,
                        reference.chapter,
                        _state.value.translation,
                    ),
                )
            }
        }
    }

    /**
     * [targetVerse] is where to land, and only the first open has one. Paging
     * passes null: an arrow means "the next chapter", which starts at its first
     * verse, not wherever this screen happened to open days ago.
     */
    private suspend fun open(
        translation: Translation,
        reference: Reference,
        targetVerse: Int?,
    ) {
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
            highlights = stored.highlightsIn(reference.bookId, reference.chapter, translation),
            loading = false,
            previous = repository.previousChapter(reference.bookId, reference.chapter),
            next = repository.nextChapter(reference.bookId, reference.chapter),
            openAtVerse = targetVerse,
        )
        store.setPosition(reference.bookId, reference.chapter, targetVerse ?: 1)
    }

    /** Pages in place, so reading a book end to end leaves one back-stack entry. */
    fun goToNext() = move { repository.nextChapter(it.bookId, it.chapter) }

    fun goToPrevious() = move { repository.previousChapter(it.bookId, it.chapter) }

    private fun move(next: (Reference) -> Reference?) {
        val target = next(_state.value.reference) ?: return
        undo.dismiss()
        viewModelScope.launch { open(_state.value.translation, target, targetVerse = null) }
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

    /** Where reading actually stopped, recorded when the scroll settles. */
    fun rememberPosition(verse: Int) {
        val reference = _state.value.reference
        viewModelScope.launch {
            store.setPosition(reference.bookId, reference.chapter, verse)
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

/** The floating arrow row: touch target, the glyph inside it, the row height. */
private const val ARROW_TARGET_UNITS = 3.5f
private const val ARROW_GLYPH_UNITS = 1.6f
private const val ARROW_ROW_UNITS = 4.5f

/**
 * How far the page fades at each edge. Both ends get the same runway: a short
 * ramp under the title read as a drop shadow rather than as the page running
 * out, which is the opposite of the intent.
 */
private const val TOP_SCRIM_UNITS = 3f
private const val BOTTOM_SCRIM_UNITS = 4.5f

/** LightTopBar is three grid units tall, and the scrim now runs behind it. */
private const val TOP_BAR_UNITS = 3f

/**
 * Where the reader's content begins: clear of the bar and of the whole scrim,
 * so a chapter opens with verse 1 at full strength. Everything the reader draws
 * in that column uses this, the text and the status messages alike. They used
 * to disagree, and the messages lost: "Opening Genesis 3" was painted at 15dp,
 * underneath a 46dp bar, which made every failure look like a blank screen.
 */
private val ReaderTopInset: Dp
    @Composable get() = (TOP_BAR_UNITS + TOP_SCRIM_UNITS).gridUnitsAsDp()

/**
 * How much of the top scrim stays fully opaque, as a fraction of its height.
 * Covers the title's own line and no more, so the fade starts right below it.
 */
private const val TOP_SCRIM_HOLD = 0.28f

/**
 * Names the verse, because the reader is looking at a wall of them.
 * Total on purpose: most drags replace nothing.
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
    private val verse: Int? = null,
) : LightScreen<Unit, ReaderViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReaderViewModel> get() = ReaderViewModel::class.java

    override fun createViewModel() = ReaderViewModel(
        store = ReaderStore(lightContext.dataStore),
        repository = BibleRepository(readAsset = { lightContext.readAsset(it) }),
        initial = Reference(bookId, chapter),
        openAtVerse = verse,
    )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val pendingUndo by viewModel.undo.pending.collectAsState()

        CardinalScreen(
            title = state.title,
            // No translation line. It is a setting the reader chose once, and
            // dropping it lets LightTopBar use its larger single-line centre,
            // which is what the chapter reference deserves.
            onBack = { goBack() },
            // The book you are in now, not the one you entered from. Paging
            // rolls across boundaries, so after Genesis 50 the reader is in
            // Exodus and the chapter list behind it is the wrong one.
            onTitleClick = {
                navigateTo({ ChapterListScreen(it, state.reference.bookId) })
            },
            floatingTopBar = true,
            action = LightBarButton.LightIcon(
                icon = LightIcons.SEARCH,
                onClick = { navigateTo(::SearchScreen) },
                contentDescription = "Search",
            ),
            overlay = {
                val page = LightThemeTokens.colors.background
                Scrim(
                    // Solid only as far as the title's own line, then a long
                    // ramp. Text is visible arriving under the bar and is gone
                    // before it can collide with the title.
                    brush = Brush.verticalGradient(
                        0f to page,
                        TOP_SCRIM_HOLD to page,
                        1f to Color.Transparent,
                    ),
                    height = (TOP_BAR_UNITS + TOP_SCRIM_UNITS).gridUnitsAsDp(),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Scrim(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, page)),
                    height = BOTTOM_SCRIM_UNITS.gridUnitsAsDp(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                ChapterArrows(state, modifier = Modifier.align(Alignment.BottomCenter))
                pendingUndo?.let {
                    UndoRow(
                        pending = it,
                        onUndo = viewModel.undo::undo,
                        // Sits on top of the arrow row rather than across it.
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = ARROW_ROW_UNITS.gridUnitsAsDp()),
                    )
                }
            },
        ) {
            when {
                state.loading -> Placeholder("Opening ${state.reference.display}…")
                state.verses.isEmpty() ->
                    Placeholder("${state.reference.display} could not be opened.")
                else -> ChapterText(
                    verses = state.verses,
                    highlights = state.highlights,
                    modifier = Modifier.fillMaxSize(),
                    scrollToVerse = state.openAtVerse,
                    topInset = ReaderTopInset,
                    onVerseTap = viewModel::toggleVerse,
                    onSelectionChanged = viewModel::commitSelection,
                    onSettledAtVerse = viewModel::rememberPosition,
                    footer = { ChapterFooter(state) },
                )
            }
        }
    }

    /**
     * Scripture fades into the edge rather than being cut by it. The top scrim
     * sits under the title bar, the bottom one under the arrows, both a ramp
     * from the page colour to nothing.
     *
     * This exists because the alternative did not work. A solid disc behind
     * each arrow punched a circular hole in the text and ate whole words as a
     * verse scrolled past. A ramp hides nothing; it just runs the text out.
     *
     * Drawn from the background token rather than from black, so it still
     * reads correctly if a light theme ever arrives.
     */
    @Composable
    private fun Scrim(brush: Brush, height: Dp, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .background(brush),
        )
    }

    /**
     * Chapter paging, floating over the text instead of in a bar. A bar spent
     * five of the screen's 31 grid units on every chapter; this spends none.
     *
     * Both slots are always laid out. At Genesis 1 and Revelation 22 the arrow
     * with nowhere to go is dropped and its space is held, so the surviving
     * arrow does not slide across. Those are the only two chapters where this
     * happens: paging rolls over book boundaries.
     */
    @Composable
    private fun ChapterArrows(state: ReaderUiState, modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(ARROW_ROW_UNITS.gridUnitsAsDp())
                .padding(horizontal = Space.comfy.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowSlot(state.previous, R.drawable.ic_chapter_previous, "Previous chapter", viewModel::goToPrevious)
            ArrowSlot(state.next, R.drawable.ic_chapter_next, "Next chapter", viewModel::goToNext)
        }
    }

    /** One arrow, or the empty space where it would have been. */
    @Composable
    private fun ArrowSlot(
        reference: Reference?,
        drawable: Int,
        label: String,
        onClick: () -> Unit,
    ) {
        val target = ARROW_TARGET_UNITS.gridUnitsAsDp()
        if (reference == null) {
            Spacer(modifier = Modifier.size(target))
            return
        }
        Box(
            modifier = Modifier
                .size(target)
                .lightClickable(onClickLabel = "$label, ${reference.display}") { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawable),
                // The Box owns the announcement; naming the image too makes
                // TalkBack read the chapter twice.
                contentDescription = null,
                colorFilter = ColorFilter.tint(LightThemeTokens.colors.content),
                modifier = Modifier.size(ARROW_GLYPH_UNITS.gridUnitsAsDp()),
            )
        }
    }

    /**
     * The same two moves as the arrows, named. Kept because at the end of the
     * text the useful question is which chapter is next, not which direction
     * it is in, and because the answer is often another book: the last chapter
     * of Leviticus offers Numbers 1, not a dead end.
     *
     * Side by side rather than stacked, so each sits on the side its arrow is
     * on. When one end has nowhere to go, its space is held and the other
     * keeps its side instead of sliding across.
     */
    @Composable
    private fun ChapterFooter(state: ReaderUiState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = Space.section.gridUnitsAsDp(),
                    // Clears the floating arrows, so the end of a chapter can
                    // be scrolled out from under them.
                    bottom = (Space.section + ARROW_ROW_UNITS).gridUnitsAsDp(),
                ),
        ) {
            HorizontalDivider(
                color = LightThemeTokens.colors.contentSecondary,
                thickness = Dp.Hairline,
                modifier = Modifier.padding(bottom = Space.comfy.gridUnitsAsDp()),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ChapterLink(
                    reference = state.previous,
                    label = "Previous",
                    alignment = Alignment.Start,
                    onClick = viewModel::goToPrevious,
                    modifier = Modifier.weight(1f),
                )
                ChapterLink(
                    reference = state.next,
                    label = "Next",
                    alignment = Alignment.End,
                    onClick = viewModel::goToNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    /** One named chapter, or the empty half where it would have been. */
    @Composable
    private fun ChapterLink(
        reference: Reference?,
        label: String,
        alignment: Alignment.Horizontal,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        if (reference == null) {
            Spacer(modifier = modifier)
            return
        }
        Column(
            modifier = modifier
                .lightClickable(onClickLabel = "$label chapter, ${reference.display}") { onClick() }
                .padding(vertical = Space.snug.gridUnitsAsDp()),
            horizontalAlignment = alignment,
        ) {
            LightText(text = reference.display, variant = LightTextVariant.Detail)
            LightText(text = label, variant = LightTextVariant.Superfine, lighten = true)
        }
    }

    @Composable
    private fun Placeholder(text: String) {
        StatusMessage(text, modifier = Modifier.padding(top = ReaderTopInset))
    }
}
