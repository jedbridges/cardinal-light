package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.ascender.cardinal.data.BibleRepository
import com.ascender.cardinal.data.Highlight
import com.ascender.cardinal.data.ReaderState
import com.ascender.cardinal.data.ReaderStore
import com.ascender.cardinal.data.Translation
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HighlightsViewModel(
    private val store: ReaderStore,
    private val repository: BibleRepository,
) : LightViewModel<Unit>() {

    val state: StateFlow<ReaderState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())

    val undo = UndoController(viewModelScope, store)

    /** Deletion belongs here, where you can see what you are deleting. */
    fun delete(highlight: Highlight) {
        viewModelScope.launch {
            store.remove(highlight)
            undo.offer("Removed ${highlight.reference}", listOf(highlight))
        }
    }

    /**
     * Only the reference is stored, so previews resolve from the assets — in
     * the translation the mark was made in, not whichever is current. A word
     * range indexes into particular wording, and showing it against a
     * different rendering would quote words nobody marked.
     */
    private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())
    val previews: StateFlow<Map<String, String>> = _previews

    fun loadPreview(highlight: Highlight) {
        val key = highlight.key
        if (_previews.value.containsKey(key)) return
        viewModelScope.launch {
            val record = repository.verse(
                Translation.fromCode(highlight.translation),
                highlight.book,
                highlight.chapter,
                highlight.verse,
            ) ?: return@launch
            _previews.value = _previews.value + (key to record.text)
        }
    }
}

private val Highlight.key: String get() = "$book:$chapter:$verse:$startWord:$endWord"

/** Every highlighted verse, newest first. Insertion order, so just reversed. */
private const val TRASH_ICON_UNITS = 1.6f

class HighlightsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HighlightsViewModel>(sealedActivity) {

    override val viewModelClass: Class<HighlightsViewModel>
        get() = HighlightsViewModel::class.java

    override fun createViewModel() = HighlightsViewModel(
        store = ReaderStore(lightContext.dataStore),
        repository = BibleRepository(readAsset = { lightContext.readAsset(it) }),
    )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val previews by viewModel.previews.collectAsState()
        val pendingUndo by viewModel.undo.pending.collectAsState()
        val highlights = state.highlights.asReversed()

        LaunchedEffect(highlights) {
            highlights.forEach { viewModel.loadPreview(it) }
        }

        CardinalScreen(
            title = "Highlights",
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
            if (highlights.isEmpty()) {
                // Names both gestures. Press-and-drag is the better one and had
                // nothing anywhere in the interface pointing at it, and this
                // screen is the only place someone looks for what marks are.
                LightText(
                    text = "Tap a verse while reading to highlight it.\n" +
                        "Press and hold to select words.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(
                        horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                        vertical = Space.base.gridUnitsAsDp(),
                    ),
                )
                return@CardinalScreen
            }

            LightScrollView(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    ),
                ) {
                    highlights.forEach { highlight ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardinalRow(
                                text = highlight.reference + madeIn(highlight, state.currentTranslation),
                                detail = previews[highlight.key]?.let { preview(it, highlight) },
                                variant = LightTextVariant.Detail,
                                modifier = Modifier.weight(1f),
                                onClickLabel = "Read ${highlight.reference}",
                                onClick = {
                                    navigateTo({
                                        ReaderScreen(
                                            it,
                                            highlight.book,
                                            highlight.chapter,
                                            highlight.verse,
                                        )
                                    })
                                },
                            )
                            // 24dp icon, 44dp target.
                            Box(
                                modifier = Modifier
                                    .size(MIN_TOUCH_TARGET)
                                    .lightClickable(
                                        onClickLabel = "Remove ${highlight.reference}",
                                    ) { viewModel.delete(highlight) },
                                contentAlignment = Alignment.Center,
                            ) {
                                LightIcon(
                                    icon = LightIcons.TRASH,
                                    size = TRASH_ICON_UNITS,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Word ranges only make sense in their own translation, so one made
     * elsewhere says so rather than looking like it applies to what you are
     * reading now.
     */
    private fun madeIn(highlight: Highlight, current: Translation): String =
        if (highlight.isWholeVerse || highlight.translation == current.code) {
            ""
        } else {
            "  ${highlight.translation}"
        }

    /** The marked words, or the opening line for a whole-verse mark. */
    private fun preview(text: String, highlight: Highlight): String {
        if (highlight.isWholeVerse) return clip(text)
        val words = text.split(" ").filter { it.isNotBlank() }
        val start = highlight.startWord!!.coerceIn(0, words.lastIndex)
        val end = highlight.endWord!!.coerceIn(start, words.lastIndex)
        val marked = clip(words.subList(start, end + 1).joinToString(" "))
        // A leading ellipsis when the mark starts mid-verse, so the line reads
        // as a quotation of what was marked rather than a broken sentence.
        return if (start > 0) "…$marked" else marked
    }

    /**
     * Cuts on a word boundary and says so. `take(90)` alone produced
     * "…with thee shall bea", which reads as a rendering fault rather than an
     * elision. Same rule as `snippet()` uses for search results.
     */
    private fun clip(text: String): String {
        val trimmed = text.trim()
        if (trimmed.length <= PREVIEW_CHARS) return trimmed
        val cut = trimmed.lastIndexOf(' ', PREVIEW_CHARS)
        val end = if (cut > PREVIEW_CHARS / 2) cut else PREVIEW_CHARS
        return trimmed.take(end).trimEnd(' ', ',', ';', ':') + "…"
    }

    private companion object {
        const val PREVIEW_CHARS = 90
    }
}
