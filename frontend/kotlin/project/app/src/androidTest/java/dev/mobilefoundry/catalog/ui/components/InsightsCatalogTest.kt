package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class InsightsCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun goal(count: Int) {
        compose.onNodeWithContentDescription("Completed sessions")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$count of 20"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(count / 20f, 0f..1f)))
    }
    @Test fun periodEmptyProjectionAndGoalSurviveRouteThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Insights"); click("Open insights dashboard"); click("Month")
        compose.onNodeWithText("420 min").assertExists()
        compose.onNodeWithContentDescription("Increase completed sessions").performScrollTo().performClick()
        goal(15)
        click("Show empty insight data")
        compose.onNodeWithText("No focus activity").assertExists()
        goal(15)
        restoration.emulateSavedInstanceStateRestore()
        goal(15); compose.onNodeWithText("No focus activity").assertExists()
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Insights"); click("Show empty insight data")
        compose.onNodeWithText("420 min").assertExists()
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        goal(15)
        click("Open insights dashboard"); restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("420 min").assertExists(); goal(15)
    }
    @Test fun categoryValuesSampleDisclosureAndDisabledGoalUseCallerPolicy() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Insights"); click("Open insights dashboard")
        click("Show focus values")
        compose.onNodeWithText("18, 24, 21, 32, 30, 41, 44 minutes").assertExists()
        compose.onNode(hasText("Design") and hasText("84 min")).assertExists()
        click("Enable goal controls")
        compose.onNodeWithContentDescription("Increase completed sessions").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Reset session goal").assertIsNotEnabled(); goal(14)
        // Return the outer vertical scroller to its header, outside the tab row's horizontal scroll scope.
        compose.onNodeWithTag("insight-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
        compose.onNodeWithText("Month").assertIsDisplayed().performClick()
        compose.onNodeWithText("Month").assertIsSelected()
        compose.onNodeWithText("420 min").assertExists()
        compose.onNodeWithText("Time by category").performScrollTo()
        compose.onNode(hasText("Design") and hasText("168 min")).assertExists()
        goal(14)
        click("Enable goal controls")
        compose.onNodeWithContentDescription("Decrease completed sessions").performScrollTo().performClick()
        goal(13); click("Reset session goal"); goal(14)
    }
}
