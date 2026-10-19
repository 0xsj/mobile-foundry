package dev.mobilefoundry.product

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class InboxScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun owned(stage: String) = assumeTrue("make connected-test owns API/users and process relaunch", InstrumentationRegistry.getArguments().getString("connectedStage") == stage)
    private fun tab(title: String) = compose.onNode(hasText(title) and isSelectable()).performClick()
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun waitFor(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun ready() = compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("inbox.media") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    private fun input(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)

    @Test fun inboxPagingUncertainWritesAndAccountIsolation() {
        owned("inbox-write"); tab("Activity"); waitFor("Sign in from Account to view your inbox.")
        tab("Account"); waitFor("Sign in to your workspace")
        input("connection.login", "starter-android-inbox"); input("connection.password", "owned starter test passphrase")
        click("Sign in"); waitFor("Selected: Personal"); tab("Activity"); ready()
        waitFor("25 unread notifications"); waitFor("Showing 20 notifications.")
        click("Load more notifications"); waitFor("Showing 25 notifications."); ready()
        compose.onAllNodesWithText("Mark read")[0].performScrollTo().performClick()
        waitFor("Refresh to confirm server state before another change.")
        compose.onNodeWithTag("inbox.media").assertIsNotEnabled()
        waitFor("25 unread notifications")
        click("Refresh inbox"); waitFor("24 unread notifications"); ready()
        compose.onNodeWithTag("inbox.filter").performScrollTo().performClick(); ready()
        click("Load more notifications"); waitFor("Showing 24 notifications."); ready()
        compose.onNodeWithTag("inbox.filter").performScrollTo().performClick(); ready()
        compose.onNodeWithTag("inbox.media").performScrollTo().performClick()
        waitFor("Refresh to confirm server state before another change.")
        compose.onNodeWithTag("inbox.media").assertIsNotEnabled()
        click("Refresh inbox"); ready(); compose.onNodeWithTag("inbox.media").assertIsOff()
        tab("Account"); click("Use Starter workspace"); waitFor("Selected: Starter workspace")
        tab("Activity"); ready(); waitFor("0 unread notifications")
        compose.onNodeWithTag("inbox.media").assertIsOn(); waitFor("Your account inbox is empty.")
        tab("Account"); click("Use Personal"); waitFor("Selected: Personal")
        compose.onNodeWithTag("connection.renew").performScrollTo().performClick()
        waitFor("Selected: Personal"); tab("Activity"); ready(); waitFor("24 unread notifications")
        compose.onNodeWithTag("inbox.media").assertIsOff()
        compose.activityRule.scenario.recreate(); tab("Activity"); ready(); waitFor("24 unread notifications")
        compose.onNodeWithTag("inbox.media").assertIsOff()
    }

    @Test fun inboxRestoresServerStateWithoutReplayingAndSignOutHidesIt() {
        owned("inbox-restore"); tab("Account"); waitFor("Selected: Personal")
        tab("Activity"); ready(); waitFor("24 unread notifications")
        compose.onNodeWithTag("inbox.media").assertIsOff()
        tab("Account"); compose.onNodeWithTag("connection.sign-out").performScrollTo().performClick()
        waitFor("Sign in to your workspace"); tab("Activity")
        waitFor("Sign in from Account to view your inbox.")
        compose.onNodeWithTag("inbox.count").assertDoesNotExist()
    }
}
