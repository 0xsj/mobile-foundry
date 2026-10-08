package dev.mobilefoundry.catalog.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.catalog.FoundryCatalogRoot
import org.junit.Rule
import org.junit.Test

class AppShellTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun fiveTabsAndMaterialChoiceSurviveSavedState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryCatalogRoot() }
        val positions = listOf("Home", "Library", "Camera", "Studio", "Account").map {
            tab(it).fetchSemanticsNode().boundsInRoot.center.x
        }
        org.junit.Assert.assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
        org.junit.Assert.assertEquals((positions.first() + positions.last()) / 2, positions[2], 2f)
        listOf("Home", "Library", "Studio", "Account").forEach { label ->
            tab(label).performClick().assertIsSelected()
            compose.onNodeWithText("Nothing here yet.").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Glass surfaces").assertIsOn().performClick().assertIsOff()
        restoration.emulateSavedInstanceStateRestore()
        tab("Account").assertIsSelected()
        compose.onNodeWithContentDescription("Glass surfaces").assertIsOff()
        tab("Home").performClick()
        tab("Account").performClick()
        compose.onNodeWithContentDescription("Glass surfaces").assertIsOff()
    }

    @Test fun catalogDetailBackRootBackAndCloseReturnToStudio() {
        compose.setContent { FoundryCatalogRoot() }
        tab("Studio").performClick()
        compose.onNodeWithText("Open catalog").performClick()
        compose.onNodeWithText("Tokens", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Reduce transparency preview").assertIsDisplayed()
        back()
        compose.onNodeWithText("Close catalog").assertIsDisplayed()
        back()
        tab("Studio").assertIsSelected()
        compose.onNodeWithText("Open catalog").performClick()
        compose.onNodeWithText("Close catalog").performClick()
        tab("Studio").assertIsSelected()
        compose.onNodeWithText("Nothing here yet.").assertIsDisplayed()
    }
}
