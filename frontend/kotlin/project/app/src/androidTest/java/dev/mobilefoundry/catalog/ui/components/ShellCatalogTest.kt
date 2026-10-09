package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class ShellCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun family(text: String) { compose.onNodeWithContentDescription("Component families").performScrollTo(); click(text) }
    private fun choose(title: String, option: String) {
        compose.onNodeWithContentDescription(title).performScrollTo().performClick()
        compose.onNode(hasText(option) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }
    @Test fun pageValuesSurviveHiddenChromeRecreationThemesRoutesAndFamilies() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce"); compose.onNodeWithContentDescription("Increase Pocket notebook").performScrollTo().performClick()
        family("Shells"); click("Open shell preview"); click("Add marker")
        compose.onNode(hasText("Activity") and hasClickAction() and isSelectable()).performClick()
        click("Add marker"); click("Add marker"); choose("Stack spacing", "Relaxed")
        click("Show bottom navigation")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Activity markers: 2").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Stack spacing").performScrollTo().assertTextContains("Relaxed")
        compose.onNode(hasText("Activity") and hasClickAction() and isSelectable()).assertDoesNotExist()
        click("Show bottom navigation")
        compose.onNode(hasText("Activity") and hasClickAction() and isSelectable()).assertIsSelected().performClick()
        compose.onNodeWithText("Tab changes: 1").performScrollTo().assertExists()
        compose.onNode(hasText("Overview") and hasClickAction() and isSelectable()).performClick()
        compose.onNodeWithText("Overview markers: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Shells")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick()
        click("Open shell preview")
        compose.onNodeWithText("Overview markers: 1").performScrollTo().assertExists()
        compose.onNode(hasText("Activity") and hasClickAction() and isSelectable()).performClick()
        compose.onNodeWithText("Activity markers: 2").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
}
