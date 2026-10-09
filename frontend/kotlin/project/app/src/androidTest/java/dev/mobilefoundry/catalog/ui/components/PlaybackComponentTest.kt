package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.slider.ValueSlider
import dev.mobilefoundry.ui.components.patterns.nowplayingcard.NowPlayingCard
import dev.mobilefoundry.ui.components.patterns.playbackcontrols.PlaybackControls
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun transportTargetsAndPassiveIdentityRemainIndependentOfMetadataActions() {
        var playing by mutableStateOf(false); var enabled by mutableStateOf(true)
        var previous = 0; var next = 0; var toggles = 0; var favorites = 0
        compose.setContent { FoundryTheme {
            NowPlayingCard("Coastline study", "Coastline study, Mira Chen", detail = "Mira Chen", artwork = { Text("Decorative cover") }, controls = {
                PlaybackControls(playing, "Previous track", if (playing) "Pause preview" else "Play preview", "Next track", if (playing) "Playing" else "Paused",
                    { previous++ }, { playing = !playing; toggles++ }, { next++ }, previousEnabled = false, toggleEnabled = enabled, nextEnabled = enabled)
            }, actions = { ActionButton({ favorites++ }) { Text("Favorite track") } })
        } }
        compose.onNodeWithContentDescription("Coastline study, Mira Chen").assertHasNoClickAction()
        compose.onNodeWithText("Decorative cover").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous track").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Play preview").performClick()
        compose.onNodeWithContentDescription("Pause preview").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
        compose.onNodeWithContentDescription("Next track").performClick()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithContentDescription("Pause preview").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Next track").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Favorite track").assertIsEnabled().performClick()
        assertEquals(0, previous); assertEquals(1, toggles); assertEquals(1, next); assertEquals(1, favorites)
    }
    @Test fun identityTimelineAndTransportTargetsFitNarrowLargeTextRTLBounds() {
        var fontScale by mutableFloatStateOf(1f); var direction by mutableStateOf(LayoutDirection.Ltr)
        var cardHeight = 0; var mark = Rect.Zero
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("playback-probe")) {
                    NowPlayingCard("A long recording title for a quiet afternoon", "Supplied track identity", Modifier.onSizeChanged { cardHeight = it.height },
                        detail = "A supplied creator and collection", artworkSize = 64.dp,
                        artwork = { Row(Modifier.fillMaxWidth()) { Box(Modifier.size(24.dp).onGloballyPositioned { mark = it.boundsInRoot() }); Spacer(Modifier.weight(1f)) } }, timeline = {
                            ValueSlider("Playback position", 45f, {}, "0:45 of 1:32", range = 0f..92f)
                        }, controls = {
                            PlaybackControls(false, "Previous track", "Play preview", "Next track", "Paused", {}, {}, {})
                        }, actions = { ActionButton({}) { Text("Favorite preview") } })
                }
            } }
        }
        compose.waitForIdle(); val normal = cardHeight; val ltr = mark
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(cardHeight > normal + 80); assertTrue(mark.left > ltr.left + 100)
        val viewport = compose.onNodeWithTag("playback-probe").fetchSemanticsNode().boundsInRoot
        for (label in listOf("Previous track", "Play preview", "Next track")) {
            compose.onNodeWithContentDescription(label).performScrollTo().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        }
        compose.onNodeWithText("Favorite preview").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Playback position").performScrollTo().assertExists()
    }
}
