package dev.mobilefoundry.catalog.ui.shell.patterns

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import dev.mobilefoundry.catalog.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductShellCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun click(text: String, scroll: Boolean = false) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (scroll) node.performScrollTo()
        node.performClick()
    }
    private fun open(pattern: String) {
        click("Studio"); click("Open catalog"); click("Product shells", true); preview(pattern)
    }
    private fun preview(pattern: String) = click("Open $pattern", true)
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
    private fun choose(label: String, option: String) {
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        compose.onNode(hasText(option) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }
    private fun waitFor(text: String) = compose.waitUntil(8_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun rotate(request: Int, orientation: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = request }
        // Wait for the native window to finish recreation before querying Compose roots.
        compose.waitUntil(8_000) {
            var ready = false
            compose.activityRule.scenario.onActivity {
                ready = it.resources.configuration.orientation == orientation && it.window.decorView.hasWindowFocus()
            }
            ready
        }
    }
    @Test fun dailyTabsKeepIndependentCountsAcrossThemesChooserAndRecreation() {
        open("Daily app"); click("Check in", true)
        compose.onNode(hasText("Activity") and isSelectable()).performClick()
        click("Check in", true); click("Check in", true)
        compose.onNodeWithText("Activity check-ins: 2").assertExists()
        click("Glass"); click("Back to shells"); preview("Daily app")
        compose.onNode(hasText("Activity") and isSelectable()).assertIsSelected()
        compose.activityRule.scenario.recreate(); waitFor("Activity check-ins: 2")
        compose.onNodeWithText("Glass").assertIsOff()
        compose.onNode(hasText("Today") and isSelectable()).performClick()
        compose.onNodeWithText("Today check-ins: 1").assertExists()
        click("Back to shells"); click("Back"); click("Product shells", true); preview("Daily app")
        compose.onNodeWithText("Today check-ins: 0").assertExists()
    }
    @Test fun workspaceSelectionAndCompletionSurviveCompactExpandedAndBack() {
        open("Workspace"); click("Research board", true); click("Complete project")
        compose.onNodeWithText("Project status: complete").assertExists()
        back(); compose.onNodeWithTag("workspace-list-pane").assertExists()
        click("Release checklist", true); click("Back to projects"); click("Research board", true)
        compose.onNodeWithText("Reopen project").assertIsDisplayed()
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            compose.onNodeWithTag("workspace-list-pane").assertIsDisplayed()
            compose.onNodeWithTag("workspace-detail-pane").assertIsDisplayed()
            val list = compose.onNodeWithTag("workspace-list-pane").fetchSemanticsNode().boundsInRoot
            val detail = compose.onNodeWithTag("workspace-detail-pane").fetchSemanticsNode().boundsInRoot
            assertTrue(list.right <= detail.left + 1)
            compose.onNodeWithText("Reopen project").assertIsDisplayed()
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            compose.onNodeWithTag("workspace-list-pane").assertDoesNotExist()
            compose.onNodeWithText("Back to projects").assertIsDisplayed()
            compose.onNodeWithText("Project status: complete").assertExists()
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    }
    @Test fun browseReturnsToItsNativeFilterAndRetainsSavedCollection() {
        open("Browse and collect")
        field("Find collections").performScrollTo().performTextReplacement("city")
        compose.onNodeWithText("Open Coastal walks").assertDoesNotExist()
        click("Open City notebooks", true); click("Save collection"); back()
        field("Find collections").assertTextContains("city")
        compose.onNodeWithText("Saved collections: 1").assertExists()
        compose.activityRule.scenario.recreate(); waitFor("Saved collections: 1")
        field("Find collections").assertTextContains("city")
        click("Open City notebooks", true); compose.onNodeWithText("Unsave collection").assertIsDisplayed()
        click("Unsave collection"); click("Back to collections")
        field("Find collections").performScrollTo().performTextReplacement("nothing matches")
        compose.onNodeWithText("No collections match your search.").assertExists()
    }
    @Test fun canvasNativeDraftToolsBoundedActionsAndReceiptsSurviveRecreation() {
        open("Canvas studio")
        field("Canvas title").performScrollTo().performTextReplacement("My canvas")
        choose("Canvas tool", "Arrange")
        repeat(7) { click("Add layer") }
        compose.onNodeWithText("Add layer").assertIsNotEnabled()
        click("Preview export"); click("Preview export")
        compose.activityRule.scenario.recreate(); waitFor("Preview exports: 2")
        field("Canvas title").performScrollTo().assertTextContains("My canvas")
        compose.onNodeWithContentDescription("Canvas tool").assertTextContains("Arrange")
        compose.onNodeWithText("Canvas layers: 8").assertExists()
        click("Back to shells"); preview("Guided setup"); click("Back to shells"); preview("Canvas studio")
        compose.onNodeWithText("Preview exports: 2").assertExists()
    }
    @Test fun setupBackReviewCompletionAndExplicitResetKeepNativeDraftHonest() {
        open("Guided setup")
        field("Your name").performScrollTo().performTextReplacement("Alex")
        compose.onNodeWithText("Continue setup").assertIsDisplayed().performClick()
        choose("Your focus", "Build"); click("Continue setup")
        compose.onNodeWithText("Name: Alex").assertExists(); compose.onNodeWithText("Focus: Build").assertExists()
        back(); compose.onNodeWithContentDescription("Your focus").assertTextContains("Build")
        click("Previous step"); field("Your name").assertTextContains("Alex")
        click("Continue setup"); click("Continue setup"); click("Finish setup")
        compose.activityRule.scenario.recreate(); waitFor("You're ready to begin")
        compose.onNodeWithText("Alex · Build").assertExists()
        click("Glass"); click("Start again")
        field("Your name").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        click("Continue setup"); compose.onNodeWithContentDescription("Your focus").assertTextContains("Explore")
        click("Previous step"); back(); compose.onNodeWithText("Product shells").assertIsDisplayed()
    }
}
