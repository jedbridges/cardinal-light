package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
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

/** Pick a translation. The licence for each one is on the About screen. */
private const val SELECT_ICON_UNITS = 1.6f

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardinalRow(
                                text = translation.displayName,
                                detail = translation.code,
                                modifier = Modifier.weight(1f),
                                onClickLabel = if (selected) null else "Use ${translation.displayName}",
                                onClick = { viewModel.select(translation) },
                            )
                            LightIcon(
                                icon = if (selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                                size = SELECT_ICON_UNITS,
                                contentDescription = if (selected) "Selected" else null,
                            )
                        }
                    }

                }
            }
        }
    }
}
