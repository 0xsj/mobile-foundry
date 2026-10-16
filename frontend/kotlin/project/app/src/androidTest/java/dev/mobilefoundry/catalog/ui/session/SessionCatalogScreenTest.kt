package dev.mobilefoundry.catalog.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.catalog.composition.SessionComposition
import dev.mobilefoundry.catalog.composition.SessionProvider
import dev.mobilefoundry.kernel.Outcome
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionCatalogScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text != "Back") node.performScrollTo()
        node.performClick()
    }
    private fun waitFor(text: String) { compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun field(title: String) = compose.onNode(hasText(title) and hasSetTextAction())
    private fun signIn(login: String) {
        field("Login").performScrollTo().performTextReplacement(login)
        field("Password").performScrollTo().performTextReplacement("catalog-demo")
        field("Password").performImeAction()
    }
    @Test fun deviceSignInRestoresAfterRouteRemovalExpiresRenewsAndSignsOut() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SessionComposition.vault(context, SessionProvider.DEVICE)
        runBlocking { assertEquals(Outcome.Ok(Unit), vault.clear()) }
        try {
            compose.setContent { FoundryTheme { MainNavigation() } }
            click("Identity and session"); waitFor("State: signed_out")
            click("Sign in"); compose.onNodeWithText("Error: Enter a password of up to 1024 characters.").performScrollTo().assertExists()
            signIn("alex@example.test"); waitFor("Alex"); compose.onNodeWithText("Account access is active.").assertExists()
            click("Back"); click("Identity and session"); waitFor("Alex")
            field("Password").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            click("Advance time 61 seconds"); waitFor("Your access has expired. Renew or sign in again.")
            click("Renew access"); waitFor("Alex")
            signIn("sam@example.test"); waitFor("Sam")
            click("Reject next renewal"); waitFor("Sign in again to continue.")
            click("Sign out"); waitFor("State: signed_out")
            click("Back"); click("Identity and session"); waitFor("State: signed_out")
            compose.onNodeWithText("No active identity").assertExists()
            runBlocking { assertEquals(Outcome.Ok(null), vault.load()) }
        } finally { runBlocking { vault.clear() } }
    }
    @Test fun memorySourceDiscardsSessionWhenItIsReplaced() {
        compose.setContent { FoundryTheme { SessionCatalogScreen({}) } }
        waitFor("No active identity")
        compose.onNodeWithContentDescription("Credentials").performScrollTo().performClick()
        compose.onNode(hasText("Memory") and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        waitFor("State: signed_out"); signIn("alex@example.test"); waitFor("Alex")
        compose.onNodeWithContentDescription("Credentials").performScrollTo().performClick()
        compose.onNode(hasText("Device") and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        waitFor("State: signed_out")
        compose.onNodeWithContentDescription("Credentials").performScrollTo().performClick()
        compose.onNode(hasText("Memory") and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        waitFor("State: signed_out"); compose.onNodeWithText("No active identity").assertExists()
    }
    @Test fun accountSwitchDropsDraftKeepsMemoryRecordsAndRenewalReopensEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SessionComposition.vault(context, SessionProvider.DEVICE)
        runBlocking { vault.clear() }
        compose.setContent { FoundryTheme { SessionCatalogScreen({}) } }
        waitFor("No active identity")
        compose.onNodeWithContentDescription("Credentials").performScrollTo().performClick()
        compose.onNode(hasText("Memory") and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        signIn("alex@example.test"); waitFor("No notes in this account.")
        field("Account note title").performScrollTo().performTextReplacement("Alex private note")
        field("Account note title").performImeAction(); waitFor("Alex private note")
        waitFor("Account live reader: Alex private note")
        field("Account note title").performScrollTo().performTextReplacement("Unsaved private draft")
        signIn("sam@example.test"); waitFor("Sam"); waitFor("No notes in this account.")
        compose.onNodeWithText("Alex private note").assertDoesNotExist()
        waitFor("Account live reader: 0 notes")
        compose.onNodeWithText("Account live reader: Alex private note").assertDoesNotExist()
        field("Account note title").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        signIn("alex@example.test"); waitFor("Alex private note")
        waitFor("Account live reader: Alex private note")
        field("Account note title").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        click("Send authenticated probe"); waitFor("GET /api/probe · 200 · alex-personal · request catalog-probe")
        compose.onNodeWithContentDescription("Probe response").performScrollTo().performClick()
        compose.onNode(hasText("Slow response") and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        click("Send authenticated probe"); click("Advance time 61 seconds"); waitFor("Account access is closed.")
        compose.onNodeWithText("Account notes").assertDoesNotExist()
        compose.onNodeWithText("Account live reader").assertDoesNotExist()
        compose.onNodeWithText("GET /api/probe · 200 · alex-personal · request catalog-probe").assertDoesNotExist()
        click("Renew access"); waitFor("Alex private note")
        waitFor("Account live reader: Alex private note")
        click("Sign out"); waitFor("State: signed_out"); compose.onNodeWithText("Account notes").assertDoesNotExist()
    }
}
