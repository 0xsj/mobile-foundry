package dev.mobilefoundry.product

import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FilesScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun owned(stage: String) = assumeTrue("Owned API and gallery required",InstrumentationRegistry.getArguments().getString("connectedStage")==stage)
    private fun tab(title: String) = compose.onNode(hasText(title) and isSelectable()).performClick()
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun waitFor(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun ready() = compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("files.choose") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
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
    @Test fun photoUploadReceiptRecoveryPreviewDeleteAndAccountIsolation() {
        owned("files-write");tab("Today");waitFor("Sign in from Account to view your files.")
        tab("Account");waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android-files")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in");waitFor("Selected: Personal");tab("Today");ready();waitFor("Showing 20 files.")
        click("Load more files");waitFor("Showing 25 files.")
        compose.onAllNodesWithText("Download preview")[0].performScrollTo().performClick();waitFor("Download verified")
        compose.onNodeWithText("Close preview").performClick()
        click("Choose photo");pickPhoto();compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.draft").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        click("Upload photo");compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.failure").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("files.pending").assertExists()
    }
    @Test fun restoreCompletionReceiptWithoutReupload() {
        owned("files-completion-restore");tab("Today");compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.pending").fetchSemanticsNodes().isNotEmpty() }
        click("Retry pending operation");ready()
        compose.onNodeWithTag("files.pending").assertDoesNotExist();click("Load more files");waitFor("Showing 26 files.")
        compose.onAllNodesWithText("Delete file")[0].performScrollTo().performClick();compose.onNodeWithText("Confirm delete").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.failure").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun restoreOriginalDeleteThenReviewTerminalConflict() {
        owned("files-delete-restore");tab("Today");compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.pending").fetchSemanticsNodes().isNotEmpty() }
        click("Retry pending operation");ready();click("Load more files");waitFor("Showing 25 files.")
        // The owned proxy commits a competing authorized delete before ours.
        compose.onAllNodesWithText("Delete file")[0].performScrollTo().performClick();compose.onNodeWithText("Confirm delete").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.failure").fetchSemanticsNodes().isNotEmpty() }
        click("Review pending operation");waitFor("Forget local recovery?")
        compose.onNodeWithText("Keep pending operation").performClick();compose.onNodeWithTag("files.pending").assertExists()
        click("Review pending operation");compose.onNodeWithText("Forget local recovery").performClick();ready()
        compose.onNodeWithTag("files.pending").assertDoesNotExist();click("Load more files");waitFor("Showing 24 files.")
        tab("Account");click("Use Starter workspace");waitFor("Selected: Starter workspace");tab("Today");ready();waitFor("Your account has no files.")
        tab("Account");click("Use Personal");waitFor("Selected: Personal")
        compose.onNodeWithTag("connection.renew").performScrollTo().performClick();waitFor("Selected: Personal")
    }
    @Test fun restoreFilesAndSignOutHidesThem() {
        owned("files-restore");tab("Account");waitFor("Selected: Personal");tab("Today");ready();waitFor("Showing 20 files.")
        tab("Account");compose.onNodeWithTag("connection.sign-out").performScrollTo().performClick()
        waitFor("Sign in to your workspace");tab("Today");waitFor("Sign in from Account to view your files.")
        compose.onNodeWithTag("files.loaded").assertDoesNotExist()
    }
    @Test fun stagePhotoBeforeUnsentContentAndProcessExit() {
        owned("files-pending-write");tab("Account");waitFor("Sign in to your workspace")
        compose.onNodeWithTag("connection.login").performScrollTo().performTextReplacement("starter-android-pending-files")
        compose.onNodeWithTag("connection.password").performScrollTo().performTextReplacement("owned starter test passphrase")
        click("Sign in");waitFor("Selected: Personal");tab("Today");ready();waitFor("Showing 0 files.")
        click("Choose photo");pickPhoto();compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.draft").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        click("Upload photo");compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.failure").fetchSemanticsNodes().isNotEmpty() };compose.onNodeWithTag("files.pending").assertExists()
    }
    @Test fun restorePhotoBytesAndExplicitlyResumeUnsentContent() {
        owned("files-pending-restore");tab("Today");compose.waitUntil(20_000) { compose.onAllNodesWithTag("files.pending").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("files.choose").assertIsNotEnabled();click("Retry pending operation");ready();waitFor("Showing 1 files.")
        compose.onNodeWithTag("files.pending").assertDoesNotExist()
        click("Download preview");waitFor("Download verified");compose.onNodeWithText("Close preview").performClick()
        tab("Account");compose.onNodeWithTag("connection.sign-out").performScrollTo().performClick();waitFor("Sign in to your workspace")
    }

}
