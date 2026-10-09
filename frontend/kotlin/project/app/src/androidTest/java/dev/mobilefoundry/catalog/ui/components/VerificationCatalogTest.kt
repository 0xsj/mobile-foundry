package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VerificationCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun family(text: String) { compose.onNodeWithContentDescription("Component families").performScrollTo(); click(text) }
    private fun choose(title: String, option: String) {
        compose.onNodeWithContentDescription(title).performScrollTo().performClick()
        compose.onNode(hasText(option) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }
    private fun code(value: String) { compose.onNodeWithText("Verification code").performScrollTo().performTextReplacement(value) }
    @Test fun nativeVerificationFlowRetainsChoicesButDiscardsCodeAndPendingCheckOnRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce"); compose.onNodeWithContentDescription("Increase Pocket notebook").performScrollTo().performClick()
        family("Verification"); click("Open verification preview")
        compose.onNode(hasText("Verify code") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        code("123-456"); click("Verify code")
        compose.onNodeWithText("Verification code").performScrollTo().assertIsNotEnabled()
        click("Advance 30 seconds")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Verification code").performScrollTo().assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Finish local check").assertDoesNotExist()
        compose.onNodeWithText("Code expires in 90 seconds").performScrollTo().assertExists()
        click("Resend code"); code("000000"); click("Verify code"); click("Finish local check")
        compose.onNodeWithText("That code did not match. Try again.").performScrollTo().assertExists()
        code("123456"); compose.onNodeWithText("That code did not match. Try again.").assertDoesNotExist()
        compose.onNodeWithText("Verification code").performImeAction(); click("Finish local check")
        compose.onNodeWithText("Preview verified").performScrollTo().assertExists()
        choose("Delivery channel", "SMS"); choose("Preview response", "Service unavailable")
        code("123456"); click("Verify code"); click("Finish local check")
        compose.onNodeWithText("The preview service is unavailable. Try again.").performScrollTo().assertExists()
        choose("Preview response", "Match demo code"); code("123")
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Verification")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open verification preview")
        compose.onNodeWithText("Verification code").performScrollTo().assertTextContains("123")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Delivery channel").performScrollTo().assertTextContains("SMS")
        compose.onNodeWithText("Verification code").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Checks started: 4 · Resend requests: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
    @Test fun staleExpiredDisabledAndDuplicateResultsCannotVerifyAnotherAttempt() {
        var values by mutableStateOf(VerificationValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { VerificationContent(values, { values = it }) } } }
        val initial = values
        compose.runOnIdle { values = values.begin().resend().advance(0).advance(-1).edit("1234567").edit("12a456") }
        assertEquals(initial, values)
        code("123456"); click("Verify code"); val canceled = values.request!!
        click("Cancel check"); click("Verify code"); val newer = values.request!!
        assertNotEquals(canceled.id, newer.id)
        compose.runOnIdle { values = values.finish(canceled, VerificationReply.ACCEPTED) }
        assertFalse(values.verified); assertEquals(newer, values.request)
        val pending = values
        compose.runOnIdle { values = values.edit("111111").begin().resend().chooseChannel(VerificationChannel.SMS).chooseResponse(VerificationResponse.UNAVAILABLE) }
        assertEquals(pending, values)
        click("Enable verification actions"); val disabled = values
        compose.runOnIdle { values = values.finish(newer, VerificationReply.ACCEPTED).edit("").begin().resend().advance().chooseChannel(VerificationChannel.SMS).chooseResponse(VerificationResponse.UNAVAILABLE).reset().cancel() }
        assertEquals(disabled, values)
        click("Enable verification actions"); click("Verify code"); val expiring = values.request!!
        compose.runOnIdle { values = values.advance(Int.MAX_VALUE).finish(expiring, VerificationReply.ACCEPTED) }
        assertFalse(values.verified); assertNull(values.request); assertEquals("", values.draft)
        compose.onNodeWithText("This code has expired. Request a new code.").performScrollTo().assertExists()
        compose.onNode(hasText("Verify code") and hasClickAction()).performScrollTo().assertIsNotEnabled().performClick()
        click("Resend code"); assertEquals(1, values.resends); assertEquals(2, values.generation)
        code("123456"); click("Verify code"); val accepted = values.request!!; click("Finish local check")
        val verified = values
        compose.runOnIdle { values = values.finish(accepted, VerificationReply.INCORRECT).begin().resend().edit("111111").advance() }
        assertEquals(verified, values); assertTrue(values.verified); assertEquals("", values.draft)
        click("Reset verification"); assertFalse(values.verified); assertEquals(verified.attempts, values.attempts)
    }
}
