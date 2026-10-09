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

class CommerceCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun total(value: String) { compose.onNode(hasText("Total") and hasText(value)).performScrollTo().assertExists() }
    private fun code(value: String) {
        val field = compose.onNode(hasSetTextAction()).performScrollTo()
        field.performTextReplacement(value); field.performImeAction()
    }
    @Test fun quantitiesAppliedCodesAndReviewValuesSurviveRoutesThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Commerce"); click("Open cart preview")
        compose.onNodeWithContentDescription("Increase Travel case").performScrollTo().assertIsNotEnabled().performClick()
        repeat(2) { compose.onNodeWithContentDescription("Increase Studio kit").performScrollTo().performClick() }
        compose.onNodeWithContentDescription("Increase Studio kit").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Increase Pocket notebook").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delivery method").performScrollTo().performClick()
        compose.onNode(hasText("Pick up") and hasClickAction()).performClick()
        total("$99.00")
        code("bad"); compose.onNodeWithText("Error: That code isn't available in this preview.").assertExists(); total("$99.00")
        code(" studio10 "); total("$89.10")
        click("Preview review")
        compose.onNodeWithContentDescription("Reviewed total: $89.10 USD").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Close review").assertDoesNotExist()
        compose.onNodeWithText("Reviews opened: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); click("Content"); click("Commerce")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open cart preview")
        total("$89.10")
        click("Clear cart"); total("$0.00")
        compose.onNodeWithText("Your cart is empty").performScrollTo().assertExists()
        compose.onNodeWithText("Preview review").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Last reviewed total: $89.10").performScrollTo().assertExists()
        click("Restore cart"); total("$26.10")
        compose.onNodeWithContentDescription("Remove discount").performScrollTo().performClick(); total("$29.00")
        click("Enable cart actions")
        compose.onNodeWithContentDescription("Increase Studio kit").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Promo code").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Preview review").performScrollTo().assertIsNotEnabled().performClick()
        total("$29.00")
    }
    @Test fun busyAdmissionAndAvailabilityChangesKeepDraftAndReviewSnapshotSeparate() {
        var values by mutableStateOf(CommerceValues(code = "STUDIO10"))
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { CommerceContent(values, { values = it }) } } }
        click("Show busy code field")
        compose.onNodeWithText("Applying code…").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Promo code").assertIsNotEnabled()
        compose.onNodeWithText("Preview review").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals("STUDIO10", values.code); assertFalse(values.discounted); assertEquals(0, values.reviews)
        click("Show busy code field"); click("Apply code"); click("Preview review")
        assertEquals(3110, values.reviewedTotal)
        compose.runOnIdle { values = values.setQuantity("notebook", 1) }
        compose.onNodeWithContentDescription("Reviewed total: $31.10 USD").assertExists()
        assertEquals(4190, values.total)
        compose.runOnIdle { values = values.copy(enabled = false) }
        compose.onNodeWithText("Close review").assertDoesNotExist()
        assertEquals(1, values.reviews); assertEquals(3110, values.reviewedTotal)
        compose.runOnIdle { values = values.copy(enabled = true).setQuantity("case", 1).setQuantity("kit", 4).setQuantity("notebook", -1) }
        assertEquals(1, values.kit); assertEquals(1, values.notebook)
        compose.runOnIdle { values = values.clearCart().review() }
        assertEquals(0, values.total); assertEquals(1, values.reviews); assertEquals(3110, values.reviewedTotal)
    }
}
