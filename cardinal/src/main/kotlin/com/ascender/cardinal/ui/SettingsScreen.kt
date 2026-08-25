package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.ascender.cardinal.data.ReaderState
import com.ascender.cardinal.data.ReaderStore
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(store: ReaderStore) : LightViewModel<Unit>() {
    val state: StateFlow<ReaderState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())
}

/**
 * The two things that are about the app rather than about scripture.
 *
 * They sat on the home screen next to reading, browsing, searching and
 * reviewing, which made six destinations of unequal weight: four you use daily
 * and two you touch once. One level down is the right depth for a preference
 * you set and forget.
 */
class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(ReaderStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()

        CardinalScreen(title = "Settings", onBack = { goBack() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp()),
            ) {
                CardinalRow(
                    text = "Translation",
                    detail = state.currentTranslation.displayName,
                    onClickLabel = "Change translation",
                    onClick = { navigateTo(::TranslationScreen) },
                )
                CardinalRow(
                    text = "About",
                    onClickLabel = "About Cardinal",
                    onClick = { navigateTo(::AboutScreen) },
                )
            }
        }
    }
}
