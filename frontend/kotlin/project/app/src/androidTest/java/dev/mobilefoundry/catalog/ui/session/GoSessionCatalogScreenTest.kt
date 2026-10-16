package dev.mobilefoundry.catalog.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test

class GoSessionCatalogScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text != "Back") node.performScrollTo()
        node.performClick()
    }
    private fun waitFor(text: String) {
        try { compose.waitUntil(12_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: ComposeTimeoutException) {
            val publicCopy = compose.onAllNodes(hasText("State:", substring = true) or hasText("storage", substring = true, ignoreCase = true)
                or hasText("request", substring = true, ignoreCase = true) or hasText("session", substring = true, ignoreCase = true)).fetchSemanticsNodes()
                .filter { !it.config.contains(SemanticsProperties.EditableText) }
                .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
            throw AssertionError("Missing $text. Public state: $publicCopy", failure)
        }
    }
    private fun field(title: String) = compose.onNode(hasText(title) and hasSetTextAction())
    private fun open(base: String) {
        click("Go identity and accounts")
        field("API base").performTextReplacement(base)
        click("Connect")
    }
    @Test fun realGoDeviceSignInSelectionRenewalAndRouteRestoration() {
        val base = InstrumentationRegistry.getArguments().getString("goSessionBase")
        assumeNotNull(base)
        compose.setContent { FoundryTheme { MainNavigation() } }
        open(base!!); waitFor("State: signed_out")
        field("Login").performScrollTo().performTextReplacement("native-ui-android")
        field("Password").performScrollTo().performTextReplacement("native integration passphrase")
        field("Password").performImeAction()
        waitFor("State: signed_in"); waitFor("Use Android workspace")
        click("Use Android workspace"); waitFor("Selected: Android workspace")
        click("Renew access"); waitFor("State: signed_in"); waitFor("Selected: Android workspace")
        click("Back"); open(base); waitFor("State: signed_in"); waitFor("Selected: Android workspace")
        click("Sign out"); waitFor("State: signed_out")
        click("Back"); open(base); waitFor("State: signed_out")
        compose.onNodeWithText("Selected: Android workspace").assertDoesNotExist()
    }
}
