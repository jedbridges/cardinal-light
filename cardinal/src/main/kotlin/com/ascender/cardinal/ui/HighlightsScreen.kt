package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HighlightsViewModel(
    store: ReaderStore,
    private val repository: BibleRepository,
) : LightViewModel<Unit>() {

    val state: StateFlow<ReaderState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())

    /**
     * The verse text is not stored with the highlight, only the reference, so
     * each row is resolved from the assets. Keeping one copy of the text in the
     * asset files rather than two also means a highlight made in one
     * translation still shows the right words after switching to another.
     */
    private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())
    val previews: StateFlow<Map<String, String>> = _previews

    fun loadPreview(highlight: Highlight, translation: Translation) {
        val key = highlight.key
        if (_previews.value.containsKey(key)) return
        viewModelScope.launch {
            val record = repository.verse(
                translation,
                highlight.book,
                highlight.chapter,
                highlight.verse,
            ) ?: return@launch
            _previews.value = _previews.value + (key to record.text)
        }
    }
}

private val Highlight.key: String get() = "$book:$chapter:$verse:$startWord:$endWord"

/**
 * Every highlighted verse, newest first.
 *
 * Highlights are stored in insertion order, so "newest first" is a reversal
 * rather than a sort on a timestamp nobody would ever see.
 */
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
        val highlights = state.highlights.asReversed()

        LaunchedEffect(highlights, state.translation) {
            highlights.forEach { viewModel.loadPreview(it, state.currentTranslation) }
        }

        CardinalScreen(title = "Highlights", onBack = { goBack() }) {
            if (highlights.isEmpty()) {
                LightText(
                    text = "Tap a verse while reading to highlight it.",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(
                        horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                        vertical = 1f.gridUnitsAsDp(),
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
                        CardinalRow(
                            text = highlight.reference,
                            detail = previews[highlight.key]?.let { preview(it, highlight) },
                            variant = LightTextVariant.Detail,
                            onClick = {
                                navigateTo({
                                    ReaderScreen(it, highlight.book, highlight.chapter)
                                })
                            },
                        )
                    }
                }
            }
        }
    }

    /** The highlighted words for a word-level mark, the opening line otherwise. */
    private fun preview(text: String, highlight: Highlight): String {
        if (highlight.isWholeVerse) return text.take(PREVIEW_CHARS).trim()
        val words = text.split(" ").filter { it.isNotBlank() }
        val start = highlight.startWord!!.coerceIn(0, words.lastIndex)
        val end = highlight.endWord!!.coerceIn(start, words.lastIndex)
        return words.subList(start, end + 1).joinToString(" ").take(PREVIEW_CHARS)
    }

    private companion object {
        const val PREVIEW_CHARS = 90
    }
}
