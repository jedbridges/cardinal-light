package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ascender.cardinal.data.Translation
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Kept next to the version in lighttool.toml by a test, so the two cannot drift. */
const val CARDINAL_VERSION = "1.1.0"

const val CARDINAL_SOURCE = "github.com/jedbridges/cardinal-light"

/**
 * What this is, who made it, and where the text came from.
 *
 * Nothing here is tappable, including the source URL. A tool cannot open a
 * link — `startActivity` is blocked and the SDK has no browser capability —
 * and the phone has no browser to open one with. So the URL is set as plain
 * secondary text rather than styled like a link that would do nothing.
 */
class AboutScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        CardinalScreen(title = "About", onBack = { goBack() }) {
            LightScrollView(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = CONTENT_PADDING_UNITS.gridUnitsAsDp(),
                    ),
                ) {
                    Paragraph(
                        "Cardinal is a Bible reader. Three translations, every " +
                            "verse on the phone, nothing to sign in to and no " +
                            "connection needed."
                    )
                    Paragraph("Adapted from Cardinal for iOS.")

                    Heading("Made by")
                    Paragraph("Jed Bridges")

                    Heading("Source")
                    Paragraph(CARDINAL_SOURCE)
                    Paragraph("Open source under the MIT licence.")

                    Heading("Scripture")
                    Translation.entries.forEach { translation ->
                        Paragraph("${translation.displayName}. ${translation.attribution}")
                    }

                    Heading("Version")
                    Paragraph(
                        text = CARDINAL_VERSION,
                        bottom = Space.section,
                    )
                }
            }
        }
    }

    @Composable
    private fun Heading(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = Space.comfy.gridUnitsAsDp(),
                    bottom = Space.hairline.gridUnitsAsDp(),
                ),
        )
    }

    @Composable
    private fun Paragraph(text: String, bottom: Float = Space.hairline) {
        LightText(
            text = text,
            variant = LightTextVariant.Detail,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.hairline.gridUnitsAsDp(), bottom = bottom.gridUnitsAsDp()),
        )
    }
}
