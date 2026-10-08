package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class ComponentCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun open() = compose.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
    private fun click(text: String, scroll: Boolean = true) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    @Test fun actionsRespectBusyDisabledAndExplicitConfirmation() {
        open()
        click("Save changes")
        compose.onNodeWithText("Actions performed: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unavailable").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Show busy state").performScrollTo().performClick()
        compose.onNodeWithText("Saving changes…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Actions performed: 1").performScrollTo().assertIsDisplayed()
        click("Remove example")
        click("Cancel", scroll = false)
        compose.onNodeWithText("Example item removed").assertDoesNotExist()
        click("Remove example")
        click("Remove item", scroll = false)
        compose.onNodeWithText("Example item removed").performScrollTo().assertIsDisplayed()
    }

    @Test fun searchEmptyAndClearRestoreTheCollection() {
        open()
        compose.onNodeWithText("Search projects").performScrollTo().performTextInput("zzzz")
        compose.onNodeWithText("No matching projects").performScrollTo().assertIsDisplayed()
        click("Clear search")
        compose.onNodeWithText("Orbit study").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No matching projects").assertDoesNotExist()
    }

    @Test fun selectionAndSettingsSurviveThemesAndSavedState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Patterns")
        compose.onNodeWithContentDescription("Notifications").performScrollTo().performClick().assertIsOff()
        click("Detailed")
        compose.onNode(hasText("Detailed") and isSelectable()).assertIsSelected()
        // Preview controls are outside the scrolling examples.
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Selected quality: Detailed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Notifications").performScrollTo().assertIsOff()
        compose.onNodeWithText("Dark preview").assertIsSelected()
        compose.onNodeWithText("Glass preview").assertIsNotSelected()
    }

    @Test fun contentRowsOpenAndDismissNativeDetailsSheet() {
        open()
        click("Content")
        click("Atlas workspace")
        compose.onNodeWithText("Project details").assertIsDisplayed()
        click("Close details", scroll = false)
        compose.onNodeWithText("Project details").assertDoesNotExist()
        compose.onNodeWithText("Preparing preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Preparing preview").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0.65f, 0f..1f))
    }

    @Test fun controlsKeepCallerStateAcrossFamiliesThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Controls")
        compose.onNodeWithText("Managed setting").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Include everything").performScrollTo()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.Indeterminate))
        click("Include everything")
        compose.onNodeWithText("Photos").assertIsOn()
        compose.onNodeWithText("Notes").assertIsOn()
        click("Photos")
        compose.onNodeWithText("Include everything")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.Indeterminate))
        click("Compact")
        click("This device")
        click("Archive", scroll = false)
        compose.onNodeWithContentDescription("Preview intensity").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.75f) }
        compose.onNodeWithContentDescription("Preview intensity").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0.75f, 0f..1f, 3))
        click("Content")
        click("Controls")
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Compact · Archive · 75% intensity").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Compact").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Photos").performScrollTo().assertIsOff()
        compose.onNodeWithText("Notes").assertIsOn()
    }

    @Test fun overlayDismissalDoesNotDispatchMenuOrDestructiveActions() {
        open()
        click("Overlays")
        click("Workspace actions")
        compose.onNodeWithText("Share workspace").assertIsNotEnabled()
        click("Duplicate workspace", scroll = false)
        compose.onNodeWithText("Last action: Workspace duplicated").performScrollTo().assertIsDisplayed()
        click("Show workspace sheet")
        compose.onNodeWithText("Workspace sheet").assertIsDisplayed()
        click("Close workspace sheet", scroll = false)
        click("Reset copies")
        click("Keep copies", scroll = false)
        compose.onNodeWithText("Last action: Workspace duplicated").performScrollTo().assertIsDisplayed()
        click("Workspace actions")
        click("Reset copies…", scroll = false)
        click("Confirm reset", scroll = false)
        compose.onNodeWithText("Last action: Copies reset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test fun avatarFallbackAndArtworkKeepOneAccessibleIdentity() {
        open()
        click("Display")
        compose.onNodeWithContentDescription("Jordan profile").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("JL").assertCountEquals(0)
        compose.onNodeWithText("Fallback while artwork is unavailable").performScrollTo().assertIsDisplayed()
        click("Show custom artwork")
        compose.onNodeWithText("Custom artwork slot").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Studio emblem").assertCountEquals(1)
        compose.onNodeWithText("Active projects").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("3 added this week").assertIsDisplayed()
    }

    @Test fun feedbackUsesExplicitActionsAndDoesNotRestoreAnOldNotice() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Feedback")
        click("Reduce loading motion")
        compose.onNodeWithText("Static placeholders").performScrollTo().assertIsDisplayed()
        click("Show loading placeholders")
        compose.onNodeWithText("Loading collection…").assertDoesNotExist()
        compose.onNodeWithText("Collection ready").performScrollTo().assertIsDisplayed()
        click("Show retry notice")
        compose.onNodeWithText("Example could not be saved.").performScrollTo().assertIsDisplayed()
        click("Dismiss notice")
        compose.onNodeWithText("Undo actions: 0 · Retry actions: 0").performScrollTo().assertIsDisplayed()
        click("Show retry notice")
        click("Retry example")
        compose.onNodeWithText("Example saved on this device.").performScrollTo().assertIsDisplayed()
        click("Undo example")
        compose.onNodeWithText("Undo actions: 1 · Retry actions: 1").performScrollTo().assertIsDisplayed()
        click("Show saved notice")
        click("Display")
        click("Feedback")
        compose.onNodeWithText("Example saved on this device.").assertDoesNotExist()
        click("Show saved notice")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Example saved on this device.").assertDoesNotExist()
        compose.onNodeWithText("Undo actions: 1 · Retry actions: 1").performScrollTo().assertIsDisplayed()
    }

    @Test fun groupedFieldsRetainNativeEditingAndPreferErrorOverHelp() {
        open()
        click("Controls")
        compose.onNodeWithText("Workspace title").performScrollTo().performTextClearance()
        click("Validate fields")
        compose.onNodeWithText("Enter both workspace and owner names.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Both names stay in this gallery.").assertDoesNotExist()
        compose.onNodeWithText("Workspace title").performScrollTo().performTextInput("Updated")
        compose.onNodeWithText("Enter both workspace and owner names.").assertDoesNotExist()
        compose.onNodeWithText("Both names stay in this gallery.").performScrollTo().assertIsDisplayed()
        click("Display")
        click("Controls")
        compose.onNodeWithText("Workspace title").performScrollTo().assertTextContains("Updated")
        compose.onNodeWithText("Owner name").assertTextContains("Jordan")
    }

    @Test fun collectionFilteringSortingAndVisibleSelectionPreserveHiddenIds() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Collections")
        click("Favorites only")
        click("Select visible")
        compose.onNodeWithText("2 visible · 2 selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Orbit study").assertDoesNotExist()
        click("Favorites only")
        click("Name")
        click("Newest", scroll = false)
        compose.onNodeWithText("Field notes").performScrollTo().assertIsOn()
        val orbit = compose.onNodeWithText("Orbit study").assertIsOff().fetchSemanticsNode().boundsInRoot
        val atlas = compose.onNodeWithText("Atlas workspace").assertIsOn().fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithText("Field notes").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Newest sorts by app-owned recency", orbit.top < atlas.top && atlas.top < field.top)
        click("Display")
        click("Collections")
        compose.onNodeWithText("Dark preview").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("3 visible · 2 selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Filter collection").performScrollTo().performTextInput("zzzz")
        compose.onNodeWithText("Nothing matches these filters").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Select visible").performScrollTo().assertIsNotEnabled()
        click("Reset collection filters")
        compose.onNodeWithText("3 visible · 2 selected").performScrollTo().assertIsDisplayed()
        click("Clear selection")
        compose.onNodeWithText("3 visible · 0 selected").performScrollTo().assertIsDisplayed()
    }
}
