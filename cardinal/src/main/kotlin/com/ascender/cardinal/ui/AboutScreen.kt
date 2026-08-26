package com.ascender.cardinal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
const val CARDINAL_VERSION = "1.2.0"

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
                    // A lead, then labelled facts, then fine print. Everything
                    // used to sit at one size, so the description, the author,
                    // the URL and the licences all read with equal weight.
                    Lead(
                        "A Bible reader. Three translations, every verse on the " +
                            "phone, nothing to sign in to and no connection needed."
                    )
                    FinePrint("Adapted from Cardinal for iOS.")

                    Label("Made by")
                    Fact("Jed Bridges")

                    Label("Source")
                    // Set apart from the prose because it is meant to be copied
                    // down, not read. It cannot be a link: a tool has no way to
                    // open one and the phone has no browser.
                    Fact(CARDINAL_SOURCE, monospace = true)
                    FinePrint("Open source under the MIT licence.")

                    Label("Scripture")
                    Translation.entries.forEach { translation ->
                        Fact(translation.displayName)
                        FinePrint(translation.attribution)
                    }

                    Label("Version")
                    Fact(CARDINAL_VERSION)
                    Spacer(modifier = Modifier.height(Space.section.gridUnitsAsDp()))
                }
            }
        }
    }

    /** The one sentence that says what this is. */
    @Composable
    private fun Lead(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.tight.gridUnitsAsDp()),
        )
    }

    @Composable
    private fun Label(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.section.gridUnitsAsDp()),
        )
    }

    @Composable
    private fun Fact(text: String, monospace: Boolean = false) {
        LightText(
            text = text,
            variant = LightTextVariant.Detail,
            monospace = monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.hairline.gridUnitsAsDp()),
        )
    }

    @Composable
    private fun FinePrint(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.hairline.gridUnitsAsDp()),
        )
    }
}
