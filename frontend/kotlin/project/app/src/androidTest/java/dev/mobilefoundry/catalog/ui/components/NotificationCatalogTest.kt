package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun family(text: String) {
        // The family tabs scroll horizontally inside the gallery's vertical scroll.
        compose.onNodeWithContentDescription("Component families").performScrollTo()
        click(text)
    }
    private fun filter(label: String) {
        compose.onNodeWithContentDescription("Inbox filter").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }
    private fun openReview() { compose.onNodeWithContentDescription("Review is ready, Your latest studio study is ready for a closer look., 2 minutes ago, Unread").performScrollTo().performClick() }
    @Test fun inboxReadArchiveUndoAndOpenedIdentitySurviveRoutesThemesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce")
        compose.onNodeWithContentDescription("Increase Pocket notebook").performScrollTo().performClick()
        family("Notifications"); click("Open inbox preview")
        compose.onNodeWithContentDescription("3 unread updates in inbox").performScrollTo().assertExists()
        filter("Unread"); openReview()
        compose.onNodeWithText("Close update").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Close update").assertDoesNotExist()
        compose.onNodeWithText("Last opened: Review is ready").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("2 unread updates in inbox").performScrollTo().assertExists()
        click("Actions for Workspace invitation")
        compose.onNode(hasText("Archive Workspace invitation") and hasClickAction()).performClick()
        compose.onNodeWithText("Update archived").performScrollTo().assertExists()
        click("Mark visible as read")
        compose.onNodeWithText("You're all caught up").performScrollTo().assertExists()
        restoration.emulateSavedInstanceStateRestore()
        click("Undo archive")
        compose.onNodeWithContentDescription("1 unread update in inbox").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Notifications")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open inbox preview")
        compose.onNodeWithText("Updates opened: 1").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("1 unread update in inbox").assertExists()
        click("Enable inbox actions")
        compose.onNodeWithText("Mark Workspace invitation as read").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Workspace invitation, A new place to gather ideas and collaborate., 1 hour ago, Unread").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("1 unread update in inbox").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
    @Test fun commandAdmissionRejectsUnknownArchivedAndDisabledTargetsAndClosesUnavailableDetails() {
        var values by mutableStateOf(NotificationValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { NotificationContent(values, { values = it }) } } }
        compose.runOnIdle { values = values.setRead("missing", true).archive("missing").open("missing") }
        assertEquals(listOf("export"), values.read); assertEquals(0, values.opens)
        filter("Unread"); openReview()
        compose.onNodeWithText("Close update").assertExists()
        assertEquals(InboxFilter.UNREAD, values.filter); assertTrue(values.read.contains("review"))
        compose.runOnIdle { values = values.copy(enabled = false) }
        compose.onNodeWithText("Close update").assertDoesNotExist()
        val disabled = values
        compose.runOnIdle { values = values.markVisibleRead().setRead("invite", true).archive("invite").open("export").chooseFilter(InboxFilter.ALL).reset() }
        assertEquals(disabled, values)
        compose.runOnIdle { values = values.copy(enabled = true).archive("review").archive("invite").archive("invite").markVisibleRead() }
        assertEquals(listOf("review", "invite"), values.archived); assertEquals("invite", values.undoID)
        assertFalse(values.read.contains("invite")); assertTrue(values.read.contains("tools"))
        val archived = values
        compose.runOnIdle { values = values.open("review").setRead("invite", true) }
        assertEquals(archived, values)
        click("Undo archive")
        assertEquals(listOf("review"), values.archived); assertEquals(listOf("invite"), values.visible.map { it.id })
        assertFalse(values.read.contains("invite")); assertEquals(1, values.opens)
    }
}
