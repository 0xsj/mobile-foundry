package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.onetimecode.CodeFormat
import dev.mobilefoundry.ui.components.forms.onetimecode.OneTimeCodeField
import dev.mobilefoundry.ui.components.patterns.verificationcard.VerificationCard
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VerificationComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeFieldAdmitsPasteAndKeepsSubmitIndependentFromAutofillAndMetadata() {
        var value by mutableStateOf(""); var enabled by mutableStateOf(true)
        var canSubmit by mutableStateOf(true); var submits = 0; var alternatives = 0
        compose.setContent { FoundryTheme {
            VerificationCard("Verify address", "j••••@example.test", "Supplied delivery identity", artwork = { Text("Decorative mail") }, content = {
                OneTimeCodeField("Four digit code", value, { value = it }, format = CodeFormat(4), enabled = enabled, canSubmit = canSubmit, onSubmit = { submits++ })
            }, actions = { ActionButton({ alternatives++ }) { Text("Choose another method") } })
        } }
        compose.onNodeWithContentDescription("Supplied delivery identity").assertHasNoClickAction()
        compose.onNodeWithText("Decorative mail").assertDoesNotExist()
        val field = compose.onNodeWithText("Four digit code")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.SmsOtpCode))
        field.performTextInput("12"); field.performImeAction(); assertEquals(0, submits)
        field.performTextReplacement("12-34"); assertEquals("1234", value); assertEquals(0, submits)
        field.performImeAction(); assertEquals(1, submits)
        field.performTextReplacement("12345"); field.performTextReplacement("12a4"); field.performTextReplacement("１２３４")
        assertEquals("1234", value)
        compose.runOnIdle { canSubmit = false }; field.performImeAction(); assertEquals(1, submits)
        compose.runOnIdle { enabled = false }
        field.assertIsNotEnabled()
        compose.onNodeWithText("Choose another method").assertIsEnabled().performClick(); assertEquals(1, alternatives)
        compose.runOnIdle { enabled = true }
        field.performTextClearance(); assertEquals("", value)
    }
    @Test fun nativeCodeAndCardSlotsGrowWithinNarrowLargeTextRTLBounds() {
        var scale by mutableFloatStateOf(1f); var height = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalLayoutDirection provides LayoutDirection.Rtl) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("verification-probe")) {
                    VerificationCard("Verify the address you use for this workspace", "A supplied destination with long readable copy", "Supplied delivery identity",
                        modifier = Modifier.onSizeChanged { height = it.height }, artwork = { Box(Modifier.size(40.dp)) }, content = {
                            OneTimeCodeField("Verification code", "123456", {}, help = "Enter or paste six digits. The caller owns verification.")
                        }, status = { Text("Code expires in 120 seconds") }, actions = { ActionButton({}) { Text("Verify code") } })
                }
            } }
        }
        compose.waitForIdle(); val normal = height
        compose.runOnIdle { scale = 2f }; compose.waitForIdle(); assertTrue(height > normal + 80)
        val viewport = compose.onNodeWithTag("verification-probe").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasSetTextAction()).performScrollTo().assertHeightIsAtLeast(48.dp)
        val field = compose.onNodeWithText("Verification code").fetchSemanticsNode().boundsInRoot
        assertTrue(field.left >= viewport.left - 1 && field.right <= viewport.right + 1)
        compose.onNode(hasText("Verify code") and hasClickAction()).performScrollTo().assertHeightIsAtLeast(48.dp)
        val action = compose.onNode(hasText("Verify code") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= viewport.left - 1 && action.right <= viewport.right + 1)
    }
}
