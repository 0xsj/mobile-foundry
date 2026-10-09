package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class TableCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun top() { compose.onNodeWithTag("ledger-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) } }
    @Test fun sortPageAndInspectionSurviveEmptyDataRoutesThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Tables"); click("Open project ledger")
        compose.onNodeWithContentDescription("Sort by Sessions").performScrollTo().performClick()
        click("Next page")
        compose.onNodeWithText("Page 2 of 3").assertExists()
        top()
        compose.onNodeWithContentDescription("Inspect Field notes").performScrollTo().performClick()
        compose.onNodeWithText("Inspections: 1").performScrollTo().assertExists()
        top(); click("Show empty records")
        compose.onNodeWithText("No project records").performScrollTo().assertExists()
        compose.onNodeWithText("Next page").performScrollTo().assertIsNotEnabled().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("No project records").assertExists()
        top(); click("Show empty records")
        compose.onNodeWithText("Page 2 of 3").performScrollTo().assertExists()
        top(); click("Enable ledger actions")
        compose.onNodeWithContentDescription("Sort by Sessions").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Next page").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Tables")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick()
        click("Open project ledger")
        compose.onNodeWithText("Sorted by Sessions, ascending").assertExists()
        compose.onNodeWithText("Page 2 of 3").performScrollTo().assertExists()
        compose.onNodeWithText("Inspections: 1").assertExists()
        compose.onNodeWithText("Next page").assertIsNotEnabled()
    }
}
