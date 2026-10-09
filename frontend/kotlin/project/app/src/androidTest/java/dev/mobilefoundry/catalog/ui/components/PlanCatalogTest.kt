package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun family(text: String) { compose.onNodeWithContentDescription("Component families").performScrollTo(); click(text) }
    private fun cycle(label: String) {
        compose.onNodeWithContentDescription("Billing choice").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }
    private fun current(value: String) { compose.onNode(hasText("Current plan") and hasText(value)).performScrollTo().assertExists() }
    @Test fun draftReviewApplicationAndUsageSurviveRestorationRoutesAndThemes() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce")
        compose.onNodeWithContentDescription("Increase Pocket notebook").performScrollTo().performClick()
        family("Plans"); click("Open plans preview")
        compose.onNodeWithText("Team unavailable").performScrollTo().assertIsNotEnabled().performClick()
        click("Choose Studio"); cycle("Yearly")
        current("Starter · Monthly")
        compose.onNodeWithContentDescription("Monthly exports, 3 of 5, 2 exports remaining").performScrollTo().assertExists()
        click("Review plan change")
        compose.onNode(hasContentDescription("Studio, 6 USD per month, 72 USD billed yearly") and hasAnyAncestor(hasTestTag("plan-review"))).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Close plan review").assertDoesNotExist()
        current("Starter · Monthly")
        compose.onNodeWithText("Last reviewed: Studio · Yearly").performScrollTo().assertExists()
        click("Review plan change")
        compose.onNodeWithText("Apply preview change").performClick()
        compose.onNodeWithText("Close plan review").assertDoesNotExist()
        current("Studio · Yearly")
        compose.onNodeWithContentDescription("Monthly exports, 3 of 50, 47 exports remaining").performScrollTo().assertExists()
        click("Reach current limit")
        compose.onNodeWithText("Simulate export").assertIsNotEnabled().performClick()
        click("Choose Starter"); click("Review plan change")
        compose.onNodeWithText("Apply preview change").performClick()
        compose.onNodeWithContentDescription("Monthly exports, 50 of 5, Current usage exceeds this plan's allowance.").performScrollTo().assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Plans")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open plans preview")
        current("Starter · Yearly")
        compose.onNodeWithContentDescription("Monthly exports, 50 of 5, Current usage exceeds this plan's allowance.").performScrollTo().assertExists()
        click("Reset usage"); click("Simulate export")
        compose.onNodeWithContentDescription("Monthly exports, 1 of 5, 4 exports remaining").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
    @Test fun pendingDisabledAndStaleReviewsRejectCommandsWithoutChangingCurrentAllowance() {
        var values by mutableStateOf(PlanValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { PlanContent(values, { values = it }) } } }
        compose.runOnIdle { values = values.export() }
        compose.onNodeWithContentDescription("Monthly exports, 4 of 5, 1 export remaining").performScrollTo().assertExists()
        compose.runOnIdle { values = values.copy(used = 3) }
        click("Choose Studio"); click("Show pending plan change")
        compose.onNodeWithText("Choose Starter").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Billing choice").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Review plan change").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Simulate export").performScrollTo().assertIsNotEnabled().performClick()
        val pending = values
        compose.runOnIdle { values = values.select(PlanTier.STARTER).chooseCycle(PlanCycle.YEARLY).review().applyReviewed().export().resetUsage().reachLimit() }
        assertEquals(pending, values)
        click("Show pending plan change"); click("Review plan change")
        compose.onNodeWithText("Close plan review").assertExists()
        compose.runOnIdle { values = values.chooseCycle(PlanCycle.YEARLY).applyReviewed() }
        compose.onNodeWithText("Close plan review").assertDoesNotExist()
        assertEquals(PlanTier.STARTER, values.current); assertEquals(0, values.applied)
        click("Review plan change")
        compose.runOnIdle { values = values.copy(enabled = false) }
        compose.onNodeWithText("Close plan review").assertDoesNotExist()
        val disabled = values
        compose.runOnIdle { values = values.select(PlanTier.STARTER).chooseCycle(PlanCycle.MONTHLY).review().applyReviewed().export().resetUsage().reachLimit() }
        assertEquals(disabled, values)
        compose.runOnIdle { values = values.copy(enabled = true).select(PlanTier.TEAM) }
        assertEquals(PlanTier.STUDIO, values.selected)
        click("Review plan change"); compose.onNodeWithText("Apply preview change").performClick()
        assertEquals(PlanTier.STUDIO, values.current); assertEquals(3, values.used); assertEquals(1, values.applied)
        compose.runOnIdle { values = values.applyReviewed() }
        assertEquals(1, values.applied)
    }
}
