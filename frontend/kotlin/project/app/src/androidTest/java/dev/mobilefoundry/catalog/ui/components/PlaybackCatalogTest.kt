package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun action(label: String) { compose.onNodeWithContentDescription(label).performScrollTo().performClick() }
    private fun family(text: String) { compose.onNodeWithContentDescription("Component families").performScrollTo(); click(text) }
    private fun choose(title: String, option: String) {
        compose.onNodeWithContentDescription(title).performScrollTo().performClick()
        compose.onNode(hasText(option) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }
    private fun position(copy: String) {
        compose.onNodeWithContentDescription("Playback position").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, copy))
    }
    @Test fun positionsSpeedRepeatAndFavoritesSurviveRecreationRoutesAndRecovery() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce"); action("Increase Pocket notebook")
        family("Playback"); click("Open playback preview")
        compose.onNodeWithContentDescription("Previous track").performScrollTo().assertIsNotEnabled().performClick()
        action("Play preview"); click("Advance 10 seconds"); position("0:10 of 1:32")
        choose("Playback speed", "1.5×")
        compose.onNodeWithContentDescription("Playback position").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        click("Advance 10 seconds"); position("1:00 of 1:32")
        action("Next track"); position("0:00 of 2:26")
        compose.onNodeWithContentDescription("Next track").assertIsNotEnabled().performClick()
        action("Play preview"); click("Advance 10 seconds"); position("0:15 of 2:26")
        action("Previous track"); position("1:00 of 1:32")
        click("Repeat current track"); action("Favorite Coastline study")
        choose("Playback scenario", "Buffering")
        compose.onNodeWithContentDescription("Play preview").performScrollTo().assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        position("1:00 of 1:32")
        compose.onNodeWithContentDescription("Playback speed").performScrollTo().assertTextContains("1.5×").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Unfavorite Coastline study").performScrollTo().assertIsEnabled()
        choose("Playback scenario", "Failed"); click("Retry playback")
        compose.onNodeWithContentDescription("Play preview").performScrollTo().assertIsEnabled()
        click("Show empty queue"); compose.onNodeWithText("Nothing queued").performScrollTo().assertExists()
        click("Show empty queue"); position("1:00 of 1:32")
        action("Next track"); position("0:15 of 2:26"); action("Previous track")
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Playback")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open playback preview")
        position("1:00 of 1:32")
        compose.onNode(hasText("Repeat current track") and isToggleable()).performScrollTo().assertIsOn()
        compose.onNodeWithContentDescription("Unfavorite Coastline study").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
    @Test fun endReplayAndRepeatHaveBoundsWhileIneligibleTransportRejectsChanges() {
        var values by mutableStateOf(PlaybackValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { PlaybackContent(values, { values = it }) } } }
        val initial = values
        compose.runOnIdle { values = values.select("missing").select("night").move(-1).move(3).seek(Double.NaN).seek(Double.POSITIVE_INFINITY).advance() }
        assertEquals(initial, values)
        compose.onNode(hasText("Night walk") and hasClickAction()).performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { values = values.seek(90.0).toggle().advance().advance() }
        assertEquals(92.0, values.position, 0.0); assertFalse(values.isPlaying); assertEquals(1, values.advances)
        action("Play preview"); assertEquals(0.0, values.position, 0.0)
        compose.runOnIdle { values = values.setRepeat(true).chooseSpeed(PlaybackSpeed.FAST).seek(90.0).advance() }
        assertEquals(13.0, values.position, 0.0); assertTrue(values.isPlaying)
        val looped = values
        compose.runOnIdle { values = values.advance(Double.MAX_VALUE).advance(-10.0).advance(0.0) }
        assertEquals(looped, values)
        choose("Playback scenario", "Buffering")
        val buffering = values
        compose.runOnIdle { values = values.toggle().seek(0.0).advance().move(1).select("orbit").chooseSpeed(PlaybackSpeed.NORMAL).setRepeat(false) }
        assertEquals(buffering, values)
        action("Favorite Coastline study"); assertEquals(listOf("coast"), values.favorites)
        choose("Playback scenario", "Failed"); click("Retry playback")
        click("Enable playback actions")
        val disabled = values
        compose.runOnIdle { values = values.toggle().seek(0.0).advance().select("orbit").move(1).favorite("coast").chooseSpeed(PlaybackSpeed.DOUBLE).setRepeat(false).chooseScenario(PlaybackScenario.FAILED).retry().setEmpty(true).resetPlayer() }
        assertEquals(disabled, values)
        compose.onNodeWithContentDescription("Play preview").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { values = values.setEnabled(true).setEmpty(true) }
        val empty = values
        compose.runOnIdle { values = values.toggle().seek(0.0).advance().select("orbit").move(1).favorite("coast").chooseSpeed(PlaybackSpeed.DOUBLE).setRepeat(false).chooseScenario(PlaybackScenario.FAILED).retry() }
        assertEquals(empty, values)
        click("Reset player")
        position("0:00 of 1:32")
        assertEquals(listOf("coast"), values.favorites); assertEquals(empty.advances, values.advances)
        assertFalse(values.repeatTrack); assertEquals(PlaybackSpeed.NORMAL, values.speed)
    }
}
