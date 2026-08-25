package com.ascender.cardinal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Every screen in the tool: theme, background, a top bar, then content.
 *
 * Padding is always in grid units. The LP3 grid is 27 x 31, so a hardcoded dp
 * value would be right on one panel and wrong on the next.
 */
@Composable
fun CardinalScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()

    LightTheme(colors = themeColors) {
        // A Box rather than a bare Column so [overlay] can pin something to the
        // bottom edge without taking height from the content above it. The undo
        // row uses this; nothing should be allowed to reflow scripture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = onBack?.let {
                        LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it)
                    },
                    center = if (subtitle != null) {
                        LightTopBarCenter.TwoLineDetail(line1 = title, line2 = subtitle)
                    } else {
                        LightTopBarCenter.Text(text = title)
                    },
                )
                content()
            }
            overlay()
        }
    }
}

/** A tappable line of text. The whole width is the target, not just the glyphs. */
@Composable
fun CardinalRow(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    variant: LightTextVariant = LightTextVariant.Copy,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = ROW_VERTICAL_PADDING_UNITS.gridUnitsAsDp()),
    ) {
        LightText(text = text, variant = variant)
        if (detail != null) {
            LightText(text = detail, variant = LightTextVariant.Superfine, lighten = true)
        }
    }
}

/** Horizontal inset shared by every scrolling body, so screens line up. */
const val CONTENT_PADDING_UNITS = 1f

/**
 * Row height in grid units, used both for padding and to tell
 * `LightLazyScrollView` how tall its uniform items are. Change one, change both.
 */
const val ROW_VERTICAL_PADDING_UNITS = 0.6f
const val ROW_HEIGHT_UNITS = 2.6f
