package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.display.featurerow.FeatureRow
import dev.mobilefoundry.ui.components.display.usagemeter.UsageMeter
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.plancard.PlanCard
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun choiceSelectionAndEligibilityStaySeparateFromNativeSlotActions() {
        var selected by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var choices = 0; var details = 0
        compose.setContent { FoundryTheme {
            PlanCard("Studio", selected, if (selected) "Selected Studio" else "Choose Studio", { selected = true; choices++ }, enabled = enabled,
                price = { Text("Caller-formatted price") }, status = { ActionButton({ details++ }) { Text("Plan details") } }, features = {
                    FeatureRow("Shared spaces", "Not included", false, "Shared spaces, not included")
                    UsageMeter("Exports", "8 of 5", "Exports, 8 of 5, exceeded", fraction = 1.6f)
                })
        } }
        compose.onNodeWithContentDescription("Shared spaces, not included").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Exports, 8 of 5, exceeded").assertHasNoClickAction()
        compose.onNodeWithText("Choose Studio").assertIsNotSelected().performClick()
        compose.onNodeWithText("Selected Studio").assertIsSelected().assertIsNotEnabled().performClick()
        compose.runOnIdle { selected = false; enabled = false }
        compose.onNodeWithText("Choose Studio").assertIsNotSelected().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Plan details").assertIsEnabled().performClick()
        assertEquals(1, choices); assertEquals(1, details)
    }
    @Test fun featurePriceUsageAndChoiceTargetsStayWithinNarrowLargeTextRTLBounds() {
        var fontScale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var height = 0; var choices = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("plans-probe")) {
                    PlanCard("A flexible plan for your next project", false, "Choose this plan", { choices++ }, Modifier.onSizeChanged { height = it.height },
                        subtitle = "Compare supplied benefits and billing meaning", price = { Text("€6 / month · €72 billed yearly") },
                        status = { ActionButton({}, Modifier.testTag("details")) { Text("Read plan details") } }, features = {
                            FeatureRow("Shared spaces for a growing team", "Not included", false, "Shared spaces, not included", detail = "Supplied detail wraps")
                        })
                    UsageMeter("Monthly exports", "8 of 5", "Monthly exports, 8 of 5, exceeded", detail = "Current usage exceeds the allowance", fraction = 1.6f)
                    UsageMeter("Workspace members", "Unlimited", "Workspace members, unlimited")
                }
            } }
        }
        compose.waitForIdle(); val normal = height
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(height > normal + 80)
        val viewport = compose.onNodeWithTag("plans-probe").fetchSemanticsNode().boundsInRoot
        for (label in listOf("Read plan details", "Choose this plan")) {
            compose.onNodeWithText(label).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        }
        compose.onNodeWithContentDescription("Workspace members, unlimited").performScrollTo().assertHasNoClickAction()
        assertEquals(1, choices)
    }
}
