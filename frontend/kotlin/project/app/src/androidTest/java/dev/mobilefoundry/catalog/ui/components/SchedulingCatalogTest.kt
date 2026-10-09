package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class SchedulingCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    @Test fun appliedSessionDayEmptyProjectionAndDraftSurviveRoutesThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Scheduling"); click("Open schedule planner")
        compose.onNodeWithContentDescription("Tuesday, October 6, 2026").performScrollTo().performClick()
        compose.onNodeWithText("Your day is open").assertExists()
        click("Apply session")
        compose.onNodeWithText("Sessions applied: 1").assertExists()
        compose.onNodeWithText("Focus session").assertExists()
        click("Show empty agenda")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Your day is open").assertExists()
        compose.onNodeWithContentDescription("Tuesday, October 6, 2026").assertIsSelected()
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Scheduling"); click("Show empty agenda")
        compose.onNodeWithText("Focus session").assertExists()
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick()
        compose.onNodeWithText("Draft time: 09:30").assertExists()
        click("Open schedule planner"); restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Sessions applied: 1").assertExists()
    }
    @Test fun reversedDatesDisableApplyWithoutChangingCommittedSessionOrAutoSelectingDay() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Scheduling"); click("Open schedule planner"); click("Apply session")
        click("Preview reversed dates")
        compose.onNodeWithText("End date must be on or after start date.").assertExists()
        compose.onNodeWithText("Apply session").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Sessions applied: 1").assertExists()
        compose.onNodeWithContentDescription("Monday, October 5, 2026, unavailable").assertIsSelected().assertIsNotEnabled()
        click("Reset dates")
        compose.onNodeWithText("Apply session").assertIsEnabled()
        click("Enable schedule controls")
        compose.onNodeWithContentDescription("Session time").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Apply session").assertIsNotEnabled()
        compose.onNodeWithTag("schedule-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
        compose.onNodeWithContentDescription("Sunday, October 11, 2026, unavailable").assertIsNotEnabled()
        click("Enable schedule controls"); click("Apply session")
        compose.onNodeWithText("Sessions applied: 2").assertExists()
    }
}
