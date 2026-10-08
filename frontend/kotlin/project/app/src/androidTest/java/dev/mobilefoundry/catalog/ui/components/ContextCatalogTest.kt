package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class ContextCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String, scroll: Boolean = true) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    @Test fun contextualHelpAndOptionsDismissWithoutCommitting() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Context")
        click("About previews")
        compose.onNodeWithText("Previews stay on this device until you choose to share them.").assertIsDisplayed()
        click("Close preview help", scroll = false)
        compose.onNodeWithText("Previews stay on this device until you choose to share them.").assertDoesNotExist()
        click("Show storage options")
        Espresso.pressBack()
        compose.onNodeWithText("Storage choices: 0").assertIsDisplayed()
        compose.onNodeWithText("Choose local storage").assertDoesNotExist()
        click("Show storage options")
        click("Close storage options", scroll = false)
        compose.onNodeWithText("Storage choices: 0").assertIsDisplayed()
        click("Show storage options")
        click("Choose local storage", scroll = false)
        compose.onNodeWithText("Storage choices: 1").assertIsDisplayed()
        click("About previews")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Previews stay on this device until you choose to share them.").assertDoesNotExist()
        compose.onNodeWithText("Storage choices: 1").performScrollTo().assertIsDisplayed()
    }

    @Test fun navigationReturnsToCallerStateAndLayoutReflows() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Context")
        click("Show storage options")
        click("Choose local storage", scroll = false)
        click("Storage details")
        compose.onNodeWithText("Local storage").assertIsDisplayed()
        click("Back to components", scroll = false)
        compose.onNodeWithText("Storage choices: 1").performScrollTo().assertIsDisplayed()
        click("Layout")
        click("Pick Atlas")
        click("Narrow content preview")
        compose.onNodeWithText("Orbit preview").assertDoesNotExist() // Artwork is exposed by content description.
        compose.onNodeWithContentDescription("Field preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Picked layout item: Atlas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dark preview").performClick()
        click("Context")
        click("Layout")
        compose.onNodeWithText("Narrow content preview").performScrollTo().assertIsOn()
        compose.onNodeWithText("Picked layout item: Atlas").performScrollTo().assertIsDisplayed()
    }
}
