package com.ascender.cardinal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ascender.cardinal.data.Highlight
import com.ascender.cardinal.data.ReaderStore
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A removal that can still be taken back. */
data class PendingUndo(val message: String, val highlights: List<Highlight>)

/**
 * The five seconds after a mark disappears, matching the iOS window. On a
 * one-handed device the remove gesture neighbours the scroll gesture.
 */
class UndoController(
    private val scope: CoroutineScope,
    private val store: ReaderStore,
) {
    private val _pending = MutableStateFlow<PendingUndo?>(null)
    val pending: StateFlow<PendingUndo?> = _pending.asStateFlow()

    private var expiry: Job? = null

    fun offer(message: String, removed: List<Highlight>) {
        if (removed.isEmpty()) return
        _pending.value = PendingUndo(message, removed)
        expiry?.cancel()
        expiry = scope.launch {
            delay(UNDO_WINDOW_MS)
            _pending.value = null
        }
    }

    fun undo() {
        val pending = _pending.value ?: return
        _pending.value = null
        expiry?.cancel()
        scope.launch { store.restore(pending.highlights) }
    }

    fun dismiss() {
        _pending.value = null
        expiry?.cancel()
    }

    private companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}

/**
 * The undo row. Paints its own background because it overlays scripture, and
 * underlines rather than boxes "Undo": the theme has no accent to build a
 * button from.
 */
@Composable
fun UndoRow(pending: PendingUndo, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LightThemeTokens.colors.background)
            .padding(
                horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                vertical = Space.snug.gridUnitsAsDp(),
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = pending.message, variant = LightTextVariant.Detail, lighten = true)
        LightText(
            text = "Undo",
            variant = LightTextVariant.Detail,
            underline = true,
            modifier = Modifier.lightClickable(onClick = onUndo),
        )
    }
}
