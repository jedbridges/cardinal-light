package com.ascender.cardinal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.ascender.cardinal.data.BibleSearch
import com.ascender.cardinal.data.ReaderStore
import com.ascender.cardinal.data.SearchResults
import com.ascender.cardinal.data.Translation
import com.ascender.cardinal.data.snippet
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val store: ReaderStore,
    private val search: BibleSearch,
) : LightViewModel<Unit>() {

    /**
     * The query lives here, not in the composition. It used to be held in a
     * remembered TextFieldState, which is disposed when you open a result,
     * while the results stayed in this ViewModel — so coming back showed a
     * screen full of hits above a field that had reverted to its placeholder.
     */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<SearchResults?>(null)
    val results: StateFlow<SearchResults?> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /**
     * Observed, not read once. The screen can outlive a translation change,
     * and results labelled with the wrong translation are worse than no
     * results: every word index and every verse belongs to one rendering.
     */
    val translation: StateFlow<Translation> = store.state
        .map { it.currentTranslation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Translation.default)

    private var running: Job? = null

    init {
        // Results belong to the translation they were found in. If that
        // changes underneath, drop them rather than mislabel them.
        viewModelScope.launch {
            translation.drop(1).collect {
                running?.cancel()
                _searching.value = false
                _results.value = null
            }
        }
    }

    fun edit() { _editing.value = true }
    fun cancelEdit() { _editing.value = false }

    fun submit(query: String) {
        _query.value = query.trim()
        _editing.value = false
        // A second submit replaces the first rather than racing it. Scanning
        // the corpus is not instant, and stale results outliving their query
        // is worse than waiting.
        running?.cancel()
        running = viewModelScope.launch {
            _searching.value = true
            _results.value = search.search(translation.value, _query.value)
            _searching.value = false
        }
    }
}

/**
 * Search a translation for a phrase.
 *
 * Typing on the LP3 goes through a full-screen editor, so this is one query at
 * a time rather than results-as-you-type, and the wait lands on submit where
 * it is expected.
 */
class SearchScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SearchViewModel>(sealedActivity) {

    override val viewModelClass: Class<SearchViewModel> get() = SearchViewModel::class.java

    override fun createViewModel() = SearchViewModel(
        store = ReaderStore(lightContext.dataStore),
        search = BibleSearch(readAsset = { lightContext.readAsset(it) }),
    )

    @Composable
    override fun Content() {
        val editing by viewModel.editing.collectAsState()
        val query by viewModel.query.collectAsState()
        val results by viewModel.results.collectAsState()
        val searching by viewModel.searching.collectAsState()
        val translation by viewModel.translation.collectAsState()
        val field = remember { TextFieldState() }
        val keyboard = remember { MutableStateFlow(defaultKeyboardOptions()) }

        if (editing) {
            LightTextInputEditor(
                title = "Search",
                state = field,
                onSubmit = { viewModel.submit(it.toString()) },
                onBack = viewModel::cancelEdit,
                keyboardOptionsFlow = keyboard,
                submitLabel = "SEARCH",
                singleLine = true,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            )
            return
        }

        CardinalScreen(title = "Search", subtitle = translation.code, onBack = { goBack() }) {
            LightTextField(
                label = "Find",
                value = query,
                placeholder = "a word or phrase",
                onClick = {
                    // Seeded from the query and selected, so a second search
                    // starts by typing over the first rather than by pressing
                    // backspace once per character.
                    field.setTextAndSelectAll(query)
                    viewModel.edit()
                },
                modifier = Modifier.padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp()),
            )

            when {
                searching -> Message("Searching ${translation.displayName}…")
                results == null -> Message("Every verse of ${translation.displayName}, offline.")
                results!!.hits.isEmpty() -> Message("Nothing matches “${results!!.query}”.")
                else -> Results(results!!)
            }
        }
    }

    @Composable
    private fun Results(results: SearchResults) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightText(
                text = results.summary,
                variant = LightTextVariant.Superfine,
                lighten = true,
                modifier = Modifier.padding(
                    start = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    end = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    top = Space.base.gridUnitsAsDp(),
                    bottom = Space.hairline.gridUnitsAsDp(),
                ),
            )
            LightScrollView(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    ),
                ) {
                    results.hits.forEach { hit ->
                        CardinalRow(
                            text = hit.reference,
                            detail = snippet(hit.text, results.query),
                            variant = LightTextVariant.Detail,
                            onClickLabel = "Read ${hit.reference}",
                            onClick = {
                                navigateTo({ ReaderScreen(it, hit.book, hit.chapter, hit.verse) })
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    vertical = Space.section.gridUnitsAsDp(),
                ),
        )
    }
}
