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
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.display.pricelabel.PriceLabel
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.inlineaction.InlineActionField
import dev.mobilefoundry.ui.components.patterns.ordersummary.OrderSummary
import dev.mobilefoundry.ui.components.patterns.productrow.ProductRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CommerceComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun fieldButtonAndKeyboardShareEligibilityWithoutClearingControlledText() {
        var draft by mutableStateOf("STUDIO10")
        var canSubmit by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var busy by mutableStateOf(false)
        var submissions = 0
        compose.setContent { FoundryTheme { InlineActionField("Invite code", draft, { draft = it }, "Apply code", canSubmit,
            { submissions++ }, enabled = enabled, isBusy = busy, help = "Use a supplied code") } }
        compose.onNodeWithText("Apply code").assertIsNotEnabled().performClick()
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(0, submissions)
        compose.runOnIdle { canSubmit = true }
        compose.onNodeWithText("Apply code").performClick()
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(2, submissions); assertEquals("STUDIO10", draft)
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Invite code").assertIsNotEnabled()
        compose.onNodeWithText("Apply code").assertIsNotEnabled().performClick()
        compose.runOnIdle { busy = false; enabled = false }
        compose.onNodeWithText("Invite code").assertIsNotEnabled()
        compose.onNodeWithText("Apply code").assertIsNotEnabled().performClick()
        assertEquals(2, submissions); assertEquals("STUDIO10", draft)
    }
    @Test fun pricesAndProductActionsStayReadableAcrossWidthLargeTextAndRTL() {
        var width by mutableStateOf(240.dp)
        var fontScale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var draft by mutableStateOf("STUDIO10")
        var rowHeight = 0; var actions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(width).verticalScroll(rememberScrollState()).testTag("commerce-probe")) {
                    ProductRow("A complete studio kit for new ideas", Modifier.onSizeChanged { rowHeight = it.height }, detail = "Product copy wraps alongside supplied artwork",
                        artwork = { Text("Decorative artwork", Modifier.size(48.dp)) }, price = {
                            PriceLabel("€1.234,50", "Supplied current and previous prices", comparison = "€1.599,00", detail = "Caller-formatted price")
                        }, status = { Text("Available") }, actions = { ActionButton({ actions++ }, Modifier.testTag("add")) { Text("Add studio kit") } })
                    InlineActionField("Promo code", draft, { draft = it }, "Apply the code", true, {}, Modifier.testTag("field"))
                    OrderSummary("Order summary", "Total", "€1.234,50", lines = { KeyValueRow("Items", "€1.234,50") },
                        footer = { ActionButton({ actions++ }, Modifier.testTag("review")) { Text("Review order") } })
                }
            } }
        }
        compose.waitForIdle(); val normal = rowHeight
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(rowHeight > normal + 40)
        compose.onNodeWithText("Decorative artwork").assertDoesNotExist()
        compose.onNodeWithContentDescription("Supplied current and previous prices").assertHasNoClickAction()
        val viewport = compose.onNodeWithTag("commerce-probe").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("add", "review")) {
            compose.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(action.left >= viewport.left - 1f && action.right <= viewport.right + 1f)
        }
        compose.runOnIdle { width = 600.dp; fontScale = 1f; direction = LayoutDirection.Ltr }
        compose.onNodeWithText("Promo code").performScrollTo().assertTextContains("STUDIO10")
        assertEquals(2, actions); assertEquals("STUDIO10", draft)
    }
}
