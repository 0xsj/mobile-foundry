package dev.mobilefoundry.product

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

class ConnectedScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun tab(title: String) = compose.onNode(hasText(title) and isSelectable()).performClick()
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun waitFor(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun owned(stage: String) = assumeTrue("make connected-test owns API/users and process relaunch", InstrumentationRegistry.getArguments().getString("connectedStage") == stage)

    private fun input(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun workspaceCurrent(text: String) = waitFor("Current: $text")
    @Test fun workspaceCreationRenameConflictAndUncertainty() {
        owned("workspace-write"); tab("Account"); waitFor("Sign in to your workspace")
        input("connection.login", "starter-android-workspaces"); input("connection.password", "owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); click("Manage workspaces"); workspaceCurrent("Personal")
        input("workspace.create-name", "Product workspace Android"); click("Create workspace"); waitFor("Created: Product workspace Android")
        compose.onNodeWithTag("workspace.close").performClick(); waitFor("Selected: Personal"); click("Use Product workspace Android"); waitFor("Selected: Product workspace Android")
        tab("Library"); waitFor("No notes in this workspace."); input("notes.title", "Workspace-owned Android note"); compose.onNodeWithTag("notes.save").performScrollTo().performClick(); waitFor("Workspace-owned Android note")
        tab("Account"); click("Manage workspaces"); workspaceCurrent("Product workspace Android")
        input("workspace.rename-name", "Renamed workspace Android"); click("Rename workspace"); workspaceCurrent("Renamed workspace Android")
        input("workspace.rename-name", "Workspace conflict Android"); click("Rename workspace")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("workspace.error").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithTag("workspace.rename").assertIsNotEnabled()
        click("Refresh current workspace"); workspaceCurrent("Concurrent workspace Android")
        input("workspace.rename-name", "Reviewed workspace Android"); click("Rename workspace"); workspaceCurrent("Reviewed workspace Android")
        input("workspace.rename-name", "Unconfirmed rename Android"); click("Rename workspace")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("workspace.error").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithTag("workspace.rename").assertIsNotEnabled()
        click("Refresh current workspace"); workspaceCurrent("Unconfirmed rename Android")
        input("workspace.create-name", "Unconfirmed workspace Android"); click("Create workspace")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("workspace.uncertain").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithTag("workspace.create").assertIsNotEnabled()
        click("Refresh current workspace"); workspaceCurrent("Unconfirmed rename Android"); compose.onNodeWithTag("workspace.create").assertIsNotEnabled()
        click("Review workspaces"); waitFor("Unconfirmed workspace Android"); click("I reviewed workspaces; allow a new create")
        compose.onNodeWithTag("workspace.close").performClick(); waitFor("Selected: Unconfirmed rename Android")
        click("Use Unconfirmed workspace Android"); waitFor("Selected: Unconfirmed workspace Android")
        tab("Library"); waitFor("No notes in this workspace."); compose.onNodeWithText("Workspace-owned Android note").assertDoesNotExist()
    }
    @Test fun workspaceRestoreSelectionAndData() {
        owned("workspace-restore"); tab("Account"); waitFor("Selected: Unconfirmed workspace Android")
        tab("Library"); waitFor("No notes in this workspace."); compose.onNodeWithText("Workspace-owned Android note").assertDoesNotExist()
        tab("Account"); click("Use Unconfirmed rename Android"); waitFor("Selected: Unconfirmed rename Android")
        tab("Library"); waitFor("Workspace-owned Android note"); tab("Account"); click("Sign out"); waitFor("Sign in to your workspace")
    }
    @Test fun securityFlowsAndLiveReadFromAnotherClient() {
        owned("security")
        val args = InstrumentationRegistry.getArguments()
        val base = args.getString("apiBase")!!; val mailbox = args.getString("mailBase")!!
        tab("Account"); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android-security")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); tab("Library"); waitFor("Live updates connected.")
        val grant = lifecycleHTTP("$base/identity/sign-in", JSONObject().put("login", "starter-android-security").put("password", "owned starter test passphrase"))
        val token = grant.getString("access_token")
        val account = lifecycleHTTP("$base/accounts/bootstrap", JSONObject().put("name", "Personal"), token).getString("id")
        lifecycleHTTP("$base/accounts/$account/notes", JSONObject().put("title", "Live Android external change"), token, UUID.randomUUID().toString())
        waitFor("Live Android external change"); tab("Account")
        compose.onNodeWithTag("identity.open").performScrollTo().performClick()
        val email = "starter-android-security@example.com"
        compose.onNodeWithTag("identity.email").performScrollTo().performTextReplacement(email)
        compose.onNodeWithTag("identity.current-password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Send verification code")
        val verify = lifecycleCode(mailbox, email, "verify_email")
        compose.onNodeWithTag("identity.code").performScrollTo().performTextReplacement(verify)
        click("Verify email"); waitFor("Email verified. Refresh the verified address to confirm.")
        compose.onNodeWithTag("identity.change-current-password").performScrollTo().performTextReplacement("owned starter test passphrase")
        compose.onNodeWithTag("identity.new-password").performScrollTo().performTextReplacement("new owned security passphrase")
        click("Change password and sign out"); waitFor("Recover access")
        compose.onNodeWithTag("identity.email").performScrollTo().performTextReplacement(email)
        click("Request reset code")
        val reset = lifecycleCode(mailbox, email, "reset_password")
        compose.onNodeWithTag("identity.code").performScrollTo().performTextReplacement(reset)
        compose.onNodeWithTag("identity.new-password").performScrollTo().performTextReplacement("recovered owned security password")
        click("Reset password"); waitFor("Password reset. Sign in with your new password.")
        compose.onNodeWithTag("identity.close").performClick()
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android-security")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("recovered owned security password")
        click("Sign in")
        waitFor("Selected: Personal")
        click("Sign out")
        waitFor("Sign in to your workspace")
    }

    @Test fun saveReadCacheAndStageOutage() {
        owned("cache-write")
        val base = InstrumentationRegistry.getArguments().getString("apiBase")!!
        tab("Account"); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android-cache")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); tab("Library"); waitFor("No notes in this workspace.")
        compose.onNodeWithTag("notes.title").performScrollTo().performTextReplacement("Saved Android first page")
        compose.onNodeWithTag("notes.save").performScrollTo().performClick(); waitFor("Saved Android first page")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("notes.title") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        cacheOutage(base, true); click("Refresh notes")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("notes.cached").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes.title").assertIsNotEnabled(); compose.onNodeWithText("Saved Android first page").assertExists()
    }
    @Test fun restoreReadCacheAndForgetWithoutReplaying() {
        owned("cache-restore")
        val base = InstrumentationRegistry.getArguments().getString("apiBase")!!
        tab("Account"); waitFor("Selected: Personal"); tab("Library")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("notes.cached").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved Android first page").assertExists(); compose.onNodeWithTag("notes.title").assertIsNotEnabled()
        tab("Account"); click("Use Starter workspace"); waitFor("Selected: Starter workspace"); tab("Library"); waitFor("No notes in this workspace.")
        compose.onNodeWithText("Saved Android first page").assertDoesNotExist()
        tab("Account"); click("Use Personal"); waitFor("Selected: Personal"); tab("Library")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("notes.cached").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes.cache.remove").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("notes.cached").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Saved Android first page").assertDoesNotExist()
        cacheOutage(base, false); click("Refresh notes")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("notes.title") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved Android first page").assertExists(); compose.onNodeWithTag("notes.cached").assertDoesNotExist()
        tab("Account"); click("Sign out"); waitFor("Sign in to your workspace"); tab("Library"); waitFor("Sign in from Account to view your notes.")
    }
    private fun cacheOutage(base: String, blocked: Boolean) {
        val grant = lifecycleHTTP("$base/identity/sign-in", JSONObject().put("login", "starter-android-cache").put("password", "owned starter test passphrase"))
        val account = lifecycleHTTP("$base/accounts/bootstrap", JSONObject().put("name", "Personal"), grant.getString("access_token")).getString("id")
        lifecycleHTTP(base.removeSuffix("/v1") + "/_acceptance/notes-reads", JSONObject().put("account_id", account).put("blocked", blocked))
    }

    @Test fun registrationCreateAndExplicitSignIn() {
        owned("registration-create"); tab("Account"); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("registration.open").performScrollTo().performClick()
        fillRegistration("STARTER-ANDROID-REGISTRATION", "New Android member", "short")
        compose.onNodeWithTag("registration.submit").performScrollTo().performClick(); waitFor("Check your account details.")
        compose.onNodeWithTag("registration.error.password").assertExists()
        assertEquals("", compose.onNodeWithTag("registration.password").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("registration.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        compose.onNodeWithTag("registration.submit").performScrollTo().performClick()
        waitFor("Account created. Sign in to open your personal workspace.")
        compose.onNodeWithTag("registration.password").assertDoesNotExist()
        compose.onNodeWithTag("registration.continue").performScrollTo().performClick(); waitFor("Sign in to your workspace")
        assertEquals("starter-android-registration", compose.onNodeWithTag("connection.login").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("", compose.onNodeWithTag("connection.password").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); tab("Library"); waitFor("No notes in this workspace.")
        compose.onNodeWithTag("notes.title").performScrollTo().performTextReplacement("Registered Android note")
        compose.onNodeWithTag("notes.save").performScrollTo().performClick(); waitFor("Registered Android note")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("notes.title") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun registrationRestoreDuplicateAndUncertainAcknowledgement() {
        owned("registration-restore"); tab("Account"); waitFor("Selected: Personal"); tab("Library"); waitFor("Registered Android note")
        tab("Account"); click("Sign out"); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("registration.open").performScrollTo().performClick()
        fillRegistration("starter-android-registration", "Replacement", "a different duplicate password")
        compose.onNodeWithTag("registration.submit").performScrollTo().performClick(); waitFor("That login is already registered.")
        compose.onNodeWithTag("registration.continue").performScrollTo().performClick(); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); click("Sign out"); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("registration.open").performScrollTo().performClick()
        fillRegistration("starter-android-registration-uncertain", "Unconfirmed Android member", "owned starter test passphrase")
        compose.onNodeWithTag("registration.submit").performScrollTo().performClick()
        waitFor("Account creation was not confirmed. The account may exist. Try signing in before submitting again.")
        assertEquals("", compose.onNodeWithTag("registration.password").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("registration.continue").performScrollTo().performClick(); waitFor("Sign in to your workspace")
        assertEquals("starter-android-registration-uncertain", compose.onNodeWithTag("connection.login").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); click("Sign out"); waitFor("Sign in to your workspace")
    }
    private fun fillRegistration(login: String, name: String, password: String) {
        compose.onNodeWithTag("registration.login").performScrollTo().performTextReplacement(login)
        compose.onNodeWithTag("registration.name").performScrollTo().performTextReplacement(name)
        compose.onNodeWithTag("registration.password").performScrollTo().performTextReplacement(password)
    }

    private fun lifecycleHTTP(url: String, body: JSONObject? = null, bearer: String? = null, command: String? = null): JSONObject {
        val connection = URI(url).toURL().openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 10_000
        connection.requestMethod = if (body == null) "GET" else "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        if (command != null) connection.setRequestProperty("Idempotency-Key", command)
        try {
            if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = connection.responseCode; assertTrue(code in listOf(200, 201, 202, 204, 404))
            val text = (if (code < 400) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            return if (text.isEmpty()) JSONObject() else JSONObject(text)
        } finally { connection.disconnect() }
    }
    private fun lifecycleCode(base: String, email: String, purpose: String): String {
        repeat(100) {
            val body = lifecycleHTTP("$base/code?email=${URLEncoder.encode(email, "UTF-8")}&purpose=$purpose")
            if (body.has("token") && !body.isNull("token")) return body.getString("token")
            Thread.sleep(50)
        }
        error("Owned SMTP fixture did not capture code")
    }

    @Test fun signInSelectWriteAndRenew() {
        owned("write"); tab("Account"); waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); waitFor("Use Starter workspace")
        click("Use Starter workspace"); waitFor("Selected: Starter workspace")
        tab("Library"); waitFor("No notes in this workspace.")
        compose.onNodeWithTag("notes.title").performScrollTo().performTextReplacement("Independent Android note")
        compose.onNodeWithTag("notes.save").performScrollTo().performClick(); waitFor("Independent Android note")
        click("Edit"); compose.onNodeWithTag("notes.title").performScrollTo().performTextReplacement("Edited Android note")
        compose.onNodeWithTag("notes.save").performScrollTo().performClick(); waitFor("Edited Android note")
        tab("Account"); click("Renew access"); waitFor("Selected: Starter workspace")
        compose.activityRule.scenario.recreate(); waitFor("Selected: Starter workspace")
        tab("Library"); waitFor("Edited Android note"); click("Delete")
        compose.onNode(hasText("Delete note") and hasClickAction()).performClick(); waitFor("No notes in this workspace.")
        stage("committed")
    }

    private fun stage(kind: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("notes.title") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes.title").performScrollTo().performTextReplacement("Recover $kind Android")
        compose.onNodeWithTag("notes.save").performScrollTo().performClick(); waitFor("Unconfirmed change")
    }
    private fun restoredPending() {
        val id = InstrumentationRegistry.getArguments().getString("pendingCommand")!!
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("notes.pending.$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes.save").assertIsNotEnabled()
    }
    @Test fun restoreCommittedAndStageUnsent() {
        owned("restore"); tab("Account"); waitFor("Selected: Starter workspace")
        tab("Library"); restoredPending()
        tab("Account"); waitFor("Use Personal"); click("Use Personal"); waitFor("Selected: Personal")
        tab("Library"); waitFor("No notes in this workspace."); compose.onNodeWithText("Unconfirmed change").assertDoesNotExist()
        tab("Account"); waitFor("Use Starter workspace"); click("Use Starter workspace"); waitFor("Selected: Starter workspace")
        tab("Library"); restoredPending(); click("Check receipt")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Unconfirmed change").fetchSemanticsNodes().isEmpty() }
        click("Delete"); compose.onNode(hasText("Delete note") and hasClickAction()).performClick(); waitFor("No notes in this workspace.")
        stage("unsent")
    }
    @Test fun restoreUnsentRetryAndSignOut() {
        owned("retry"); tab("Account"); waitFor("Selected: Starter workspace"); tab("Library"); restoredPending()
        click("Check receipt")
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("No committed receipt", substring=true, ignoreCase=true)).fetchSemanticsNodes().isNotEmpty() }
        restoredPending(); click("Retry same change")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Unconfirmed change").fetchSemanticsNodes().isEmpty() }
        waitFor("Recover unsent Android"); click("Delete")
        compose.onNode(hasText("Delete note") and hasClickAction()).performClick(); waitFor("No notes in this workspace.")
        tab("Account"); click("Sign out"); waitFor("Sign in to your workspace")
        compose.activityRule.scenario.recreate(); tab("Library"); waitFor("Sign in from Account to view your notes.")
    }
}
