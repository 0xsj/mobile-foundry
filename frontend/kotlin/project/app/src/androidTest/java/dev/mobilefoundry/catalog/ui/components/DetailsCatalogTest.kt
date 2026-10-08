package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailsCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String, scroll: Boolean = true) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (scroll) node.performScrollTo()
        node.performClick()
    }
    private fun more() = compose.onNodeWithContentDescription("More copies").performScrollTo().performClick()
    private fun fewer() = compose.onNodeWithContentDescription("Fewer copies").performScrollTo().performClick()

    @Test fun chipsAndBoundedQuantityRetainCallerStateAcrossThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Details")
        compose.onNodeWithText("Print size").performScrollTo().assertIsNotEnabled()
        click("Compact size")
        compose.onNodeWithText("Compact size").assertIsSelected()
        compose.onNodeWithText("Full size").assertIsNotSelected()
        compose.onNodeWithContentDescription("Fewer copies").performScrollTo().assertIsNotEnabled()
        more(); more(); more()
        compose.onNodeWithText("5 copies").assertIsDisplayed()
        compose.onNodeWithContentDescription("More copies").assertIsNotEnabled()
        fewer(); fewer(); fewer()
        compose.onNodeWithText("0 copies").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fewer copies").assertIsNotEnabled()
        click("Enable quantity control")
        compose.onNodeWithContentDescription("More copies").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Export size: Compact size").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enable quantity control").performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("More copies").performScrollTo().assertIsNotEnabled()
    }

    @Test fun disclosureRetainsHoistedEditingWhenCollapsedAndAcrossFamilies() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Details")
        click("Delivery note")
        compose.onNodeWithText("Package note").performScrollTo().performTextClearance()
        compose.onNodeWithText("Package note").performTextInput("Keep upright")
        click("Done editing note")
        click("Delivery note")
        compose.onNodeWithText("Package note").assertDoesNotExist()
        compose.onNodeWithText("Delivery note").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Collapsed"))
        click("Delivery note")
        compose.onNodeWithText("Package note").performScrollTo().assertTextContains("Keep upright")
        click("Content"); click("Details")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Package note").performScrollTo().assertTextContains("Keep upright")
    }

    @Test fun detailBodyScrollKeepsActionsVisibleAndResetDoesNotApply() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Details")
        more()
        click("Open delivery preview")
        val before = compose.onNodeWithText("Apply preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Delivery note").performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithText("Apply preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("The footer stays outside the scrolling body", before.top, after.top, 1f)
        click("Apply preview", scroll = false)
        compose.onNodeWithText("Applied previews: 1").assertIsDisplayed()
        click("Reset delivery", scroll = false)
        compose.onNodeWithText("Apply preview").assertIsNotEnabled()
        compose.onNodeWithText("Applied previews: 1").assertIsDisplayed()
        click("Back to components", scroll = false)
        compose.onNodeWithText("0 copies").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Applied previews: 1").performScrollTo().assertIsDisplayed()
    }
}
