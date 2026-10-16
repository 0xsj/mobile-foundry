package dev.mobilefoundry.catalog.ui.physics

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import dev.mobilefoundry.catalog.MainActivity
import org.junit.Rule
import org.junit.Test

class PhysicsPlaygroundTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun open() {
        compose.onNode(hasText("Studio") and hasClickAction()).performClick()
        compose.onNodeWithText("Open catalog").performClick()
        compose.onNodeWithText("Physics playground").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("physics-court").assertExists()
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun launchControlled() {
        // Native scroll semantics animate: let scrolling settle before freezing frame time.
        compose.onNodeWithTag("physics-launch").performScrollTo()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("physics-launch").performClick()
    }
    @Test fun nativeLaunchPauseResumeScoreAndReset() {
        open(); launchControlled()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("physics-pause").performClick()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("physics-status").assertTextEquals("Paused")
        compose.onNodeWithTag("physics-score").assertTextEquals("0 / 1")
        compose.onNodeWithTag("physics-pause").performClick(); compose.mainClock.advanceTimeBy(2500)
        compose.onNodeWithTag("physics-status").assertTextEquals("Basket!")
        compose.onNodeWithTag("physics-score").assertTextEquals("1 / 1")
        compose.onNodeWithTag("physics-reset").performClick(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("physics-status").assertTextEquals("Ready to throw")
        compose.onNodeWithTag("physics-score").assertTextEquals("1 / 1")
        compose.onNodeWithTag("physics-launch").assertIsEnabled()
    }
    @Test fun nativeDragLaunchUsesTheSamePhysicsAndScores() {
        open()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("physics-court").performTouchInput {
            val start = androidx.compose.ui.geometry.Offset(width*.115f, height*(1-.65f/7))
            down(start)
            moveTo(start + androidx.compose.ui.geometry.Offset(width*(5f/2.8f/10), -height*(8.6f/2.8f/7)), delayMillis = 250)
            up()
        }
        compose.mainClock.advanceTimeBy(2500)
        compose.onNodeWithTag("physics-status").assertTextEquals("Basket!")
        compose.onNodeWithTag("physics-score").assertTextEquals("1 / 1")
    }
    @Test fun landingTargetAndNativeSlidersResetAndScoreFirstContact() {
        open(); click("physics-zone")
        compose.onNodeWithContentDescription("Launch angle").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        compose.onNodeWithContentDescription("Launch power").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7.78f) }
        launchControlled(); compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("physics-status").assertTextEquals("On target!")
        compose.onNodeWithTag("physics-score").assertTextEquals("1 / 1")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Gravity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        compose.onNodeWithTag("physics-status").assertTextEquals("Ready to throw")
        compose.onNodeWithTag("physics-score").assertTextEquals("1 / 1")
    }
}
