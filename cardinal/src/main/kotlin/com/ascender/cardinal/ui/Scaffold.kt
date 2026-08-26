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
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * LightBottomBar is four grid units tall and adds one of top margin. The SDK
 * keeps both constants private, so [CardinalScreen] repeats the total here to
 * float an overlay clear of the bar instead of underneath it.
 */
private const val BOTTOM_BAR_UNITS = 5f

/** Every screen: theme, background, top bar, content. */
@Composable
fun CardinalScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    action: LightTopBarButton? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()

    LightTheme(colors = themeColors) {
        // Box so [overlay] can pin to the bottom without reflowing content.
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
                    rightButton = action,
                )
                // Only a screen with a bottom bar needs its content constrained;
                // every other screen keeps the layout it already had.
                if (bottomBar != null) {
                    Column(modifier = Modifier.weight(1f)) { content() }
                    bottomBar()
                } else {
                    content()
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(
                        bottom = if (bottomBar != null) {
                            BOTTOM_BAR_UNITS.gridUnitsAsDp()
                        } else {
                            0.dp
                        },
                    ),
                content = overlay,
            )
        }
    }
}

/** A tappable line. The full width is the target, not just the glyphs. */
@Composable
fun CardinalRow(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    variant: LightTextVariant = LightTextVariant.Copy,
    onClickLabel: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = ROW_VERTICAL_PADDING_UNITS.gridUnitsAsDp()),
    ) {
        LightText(text = text, variant = variant)
        if (detail != null) {
            LightText(text = detail, variant = LightTextVariant.Superfine, lighten = true)
        }
    }
}

/**
 * The spacing scale, in LightOS grid units. The grid is 27 x 31, so a unit is
 * about 15dp and scales with the panel. Add a step rather than writing a bare
 * `1.3f.gridUnitsAsDp()`.
 */
object Space {
    /** Between lines of a single block of prose. */
    const val hairline = 0.25f

    /** Between verses, and between cells in a grid. */
    const val tight = 0.5f

    /** Inside a tappable row, above and below its text. */
    const val snug = 0.75f

    /** The standard inset. Screen edges, and gaps between related things. */
    const val base = 1f

    /** Between a heading and what it heads. */
    const val comfy = 1.5f

    /** Between one section of a screen and the next. */
    const val section = 2f
}

/**
 * The smallest a tappable thing may be. Rows and grid cells clear it through
 * the spacing scale; an icon has to be given the room explicitly.
 */
val MIN_TOUCH_TARGET = 44.dp

/** Horizontal inset shared by every scrolling body, so screens line up. */
const val CONTENT_PADDING_UNITS = Space.base

const val ROW_VERTICAL_PADDING_UNITS = Space.snug
