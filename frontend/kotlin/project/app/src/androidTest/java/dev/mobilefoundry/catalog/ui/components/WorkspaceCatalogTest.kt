package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class WorkspaceCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    @Test fun projectStarsAndCompactDetailSurviveRoutesThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Workspace"); click("Open adaptive workspace")
        click("Orbit study"); click("Star this project")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("workspace-projects").assertDoesNotExist()
        compose.onNodeWithText("Star this project").assertIsOn()
        compose.onNodeWithContentDescription("Orbit study, current location").assertHasNoClickAction()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("All projects, current location").assertHasNoClickAction()
        compose.onNode(hasText("Orbit study") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Workspace")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick()
        click("Open adaptive workspace")
        click("Orbit study"); compose.onNodeWithText("Star this project").assertIsOn()
        compose.onNode(hasText("All projects") and hasClickAction()).performClick()
        compose.onNodeWithTag("workspace-projects").assertExists()
        compose.onNode(hasText("Orbit study") and hasClickAction()).assertIsSelected()
    }
}
