package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class EditingCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun open() { click("Editing"); click("Open library editor") }
    private fun item(title: String) {
        compose.onNodeWithTag("editing-list").performScrollToNode(hasText(title) and isToggleable())
        compose.onNode(hasText(title) and isToggleable()).performClick()
    }
    private fun lazyText(text: String) {
        compose.onNodeWithTag("editing-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    @Test fun hiddenSelectionBulkRemovalAndUndoKeepArchiveAndIdentity() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        open(); item("Orbit study"); item("Field notes")
        compose.onNodeWithTag("editing-list").performScrollToNode(hasText("Filter library"))
        compose.onNodeWithText("Filter library").performTextInput("Orbit")
        compose.onNodeWithText("2 selected · 1 hidden").assertIsDisplayed()
        compose.onNodeWithText("Archive selected").performClick()
        compose.onNodeWithText("Remove selected").performClick()
        compose.onNodeWithText("0 selected · 0 hidden").assertIsDisplayed()
        lazyText("No matching library items. Clear the filter or undo removal.")
        compose.onNodeWithText("Undo removal").performClick()
        compose.onNodeWithText("2 selected · 1 hidden").assertIsDisplayed()
        compose.onNodeWithTag("editing-list").performScrollToNode(hasText("Orbit study"))
        compose.onNode(hasText("Orbit study") and isToggleable()).assertIsOn()
        compose.onNodeWithText("Archived").assertExists()
        compose.onNodeWithText("Clear selection").performClick()
        compose.onNodeWithText("Archive selected").assertIsNotEnabled()
    }
    @Test fun nativeSwipesResetAndUndoRestorationDoesNotReplayRemoval() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        open()
        compose.onNodeWithTag("editing-list").performScrollToNode(hasTestTag("editing-row-orbit"))
        compose.onNodeWithTag("editing-row-orbit").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Archived").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editing-row-orbit").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Removed 1 preview items").fetchSemanticsNodes().isNotEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Undo removal").performClick()
        compose.onNodeWithTag("editing-list").performScrollToNode(hasTestTag("editing-row-orbit"))
        compose.onNode(hasText("Orbit study") and isToggleable()).assertIsOff()
        compose.onNodeWithText("Archived").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("editing-list").performScrollToNode(hasTestTag("editing-row-orbit"))
        compose.onNodeWithText("Removed 1 preview items").assertDoesNotExist()
        compose.onNodeWithTag("editing-row-orbit").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Archived").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Archived").assertDoesNotExist()
    }
    @Test fun tagsImeAdmissionAndDraftSurviveThemeFamilyBackAndRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Editing")
        val field = compose.onNodeWithText("New library tag")
        field.performScrollTo().performTextInput(" sketch ")
        field.performImeAction()
        field.assertTextContains(" sketch ")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "This tag is already present."))
        field.performTextReplacement("Light")
        field.performImeAction()
        compose.onNodeWithContentDescription("Remove tag Light").assertExists()
        field.performTextReplacement("Unfinished")
        compose.onNodeWithContentDescription("Remove tag Review").performClick()
        click("Enable editing controls")
        field.performScrollTo().assertIsNotEnabled().assertTextContains("Unfinished")
        compose.onNodeWithContentDescription("Remove tag Sketch").performScrollTo().assertIsNotEnabled()
        click("Enable editing controls"); click("Open library editor")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("New library tag").assertTextContains("Unfinished")
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Editing")
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        compose.onNodeWithText("New library tag").performScrollTo().assertTextContains("Unfinished")
        compose.onNodeWithContentDescription("Remove tag Light").assertExists()
        compose.onNodeWithContentDescription("Remove tag Review").assertDoesNotExist()
    }
}
