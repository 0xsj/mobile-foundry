package dev.mobilefoundry.product

import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MediaScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun owned(stage: String) = assumeTrue("Owned API and gallery required",InstrumentationRegistry.getArguments().getString("connectedStage")==stage)
    private fun tab(title: String) = compose.onNode(hasText(title) and isSelectable()).performClick()
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun waitFor(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun ready(tag: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    private fun pickPhoto() {
        fun find(node: AccessibilityNodeInfo?, done: Boolean = false): AccessibilityNodeInfo? {
            if (node==null) return null
            if (if (done) node.text?.toString()=="Done" else node.contentDescription?.toString()?.contains("Photo taken",ignoreCase=true)==true) return node
            for (index in 0 until node.childCount) find(node.getChild(index),done)?.let { return it }
            return null
        }
        fun click(node: AccessibilityNodeInfo) {
            var target: AccessibilityNodeInfo?=node
            while (target!=null && !target.isClickable) target=target.parent
            check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
        }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline=System.currentTimeMillis()+20_000
        while (System.currentTimeMillis()<deadline) {
            val node=find(automation.rootInActiveWindow)
            if (node!=null) {
                click(node)
                // API 36's picker confirms a single selection with Done.
                while (System.currentTimeMillis()<deadline) {
                    find(automation.rootInActiveWindow,true)?.let { click(it);return }
                    Thread.sleep(100)
                }
                error("The system picker did not expose Done")
            }
            Thread.sleep(100)
        }
        error("The owned gallery photo was not exposed by the system picker")
    }
    private fun upload(count: Int, loseReply: Boolean = false) {
        tab("Today");ready("files.choose");click("Choose photo");pickPhoto()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.draft").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        ready("files.upload");click("Upload photo")
        if (loseReply) {
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.failure").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("files.pending").assertExists();click("Retry pending operation")
        }
        ready("files.choose");waitFor("Showing $count files.");compose.onNodeWithTag("files.pending").assertDoesNotExist()
    }
    private fun inbox(unread: Int, loaded: Int) {
        tab("Activity");ready("inbox.media")
        repeat(10) {
            val count=compose.onAllNodesWithText("$unread unread notifications").fetchSemanticsNodes().isNotEmpty()
            val rows=compose.onAllNodesWithText("Showing $loaded notifications.").fetchSemanticsNodes().isNotEmpty()
            if (count && rows) return
            click("Refresh inbox");ready("inbox.media")
        }
        waitFor("$unread unread notifications");waitFor("Showing $loaded notifications.")
    }
    @Test fun uploadsCreateScopedNoticesAndRespectPreferencesWithoutReplay() {
        owned("media-write");tab("Account");waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android-media")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in");waitFor("Selected: Personal");inbox(0,0)
        upload(1,true);inbox(1,1)
        click("Open file");ready("destination.download");compose.onNodeWithTag("destination.name").assertExists()
        compose.onNodeWithTag("destination.download").performScrollTo().performClick();waitFor("Download verified");click("Close file")
        waitFor("1 unread notifications")
        click("Mark read");waitFor("Refresh to confirm server state before another change.")
        compose.onNodeWithTag("inbox.media").assertIsNotEnabled()
        click("Refresh inbox");ready("inbox.media");waitFor("0 unread notifications")
        compose.onNodeWithTag("inbox.media").performScrollTo().performClick()
        waitFor("Refresh to confirm server state before another change.");click("Refresh inbox");ready("inbox.media")
        compose.onNodeWithTag("inbox.media").assertIsOff()
        upload(2);inbox(0,1)
    }
    @Test fun restoreBothFeaturesWithoutReplayingAndSignOutHidesThem() {
        owned("media-restore");tab("Account");waitFor("Selected: Personal")
        tab("Today");ready("files.choose");waitFor("Showing 2 files.");inbox(0,1)
        compose.onNodeWithTag("inbox.media").assertIsOff()
        compose.onNodeWithTag("inbox.media").performScrollTo().performClick();ready("inbox.media")
        compose.onNodeWithTag("inbox.media").assertIsOn()
        upload(3);inbox(1,2)
        tab("Today");ready("files.choose")
        compose.onAllNodesWithText("Download preview")[0].performScrollTo().performClick();waitFor("Download verified")
        compose.onNodeWithText("Close preview").performClick()
        tab("Account");click("Use Starter workspace");waitFor("Selected: Starter workspace")
        tab("Today");ready("files.choose");waitFor("Showing 0 files.");inbox(0,0)
        tab("Account");click("Use Personal");waitFor("Selected: Personal")
        compose.onNodeWithTag("connection.renew").performScrollTo().performClick();waitFor("Selected: Personal");inbox(1,2)
        compose.activityRule.scenario.recreate();tab("Today");ready("files.choose");waitFor("Showing 3 files.");inbox(1,2)
        compose.onAllNodesWithText("Open file")[0].performScrollTo().performClick();waitFor("This file is no longer available.")
        compose.onNodeWithTag("destination.name").assertDoesNotExist();click("Close file");waitFor("1 unread notifications")
        tab("Account");compose.onNodeWithTag("connection.sign-out").performScrollTo().performClick();waitFor("Sign in to your workspace")
        tab("Activity");waitFor("Sign in from Account to view your inbox.")
        tab("Today");waitFor("Sign in from Account to view your files.")
    }
}
