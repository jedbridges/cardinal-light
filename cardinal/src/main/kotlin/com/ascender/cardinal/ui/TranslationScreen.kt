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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TranslationViewModel(private val store: ReaderStore) : LightViewModel<Unit>() {
    val state: StateFlow<ReaderState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())

    fun select(translation: Translation) {
        viewModelScope.launch { store.setTranslation(translation) }
    }
}

/**
 * Pick a translation, and read the attribution while you are here.
 *
 * All three are free of copyright on the text, which is the only reason they
 * can ship inside an open-source tool that Light builds from a public commit.
 * The attribution lines are shown rather than buried because that is the whole
 * argument for why this app is allowed to exist.
 */
class TranslationScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, TranslationViewModel>(sealedActivity) {

    override val viewModelClass: Class<TranslationViewModel>
        get() = TranslationViewModel::class.java

    override fun createViewModel() = TranslationViewModel(ReaderStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()

        CardinalScreen(title = "Translation", onBack = { goBack() }) {
            LightScrollView(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    ),
                ) {
                    Translation.entries.forEach { translation ->
                        val selected = translation == state.currentTranslation
                        CardinalRow(
                            text = if (selected) {
                                "${translation.displayName}  ·"
                            } else {
                                translation.displayName
                            },
                            detail = translation.code,
                            onClick = { viewModel.select(translation) },
                        )
                    }

                    LightText(
                        text = "Attribution",
                        variant = LightTextVariant.Superfine,
                        lighten = true,
                        modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp()),
                    )
                    Translation.entries.forEach { translation ->
                        LightText(
                            text = "${translation.code}. ${translation.attribution}",
                            variant = LightTextVariant.Superfine,
                            lighten = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.4f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}
