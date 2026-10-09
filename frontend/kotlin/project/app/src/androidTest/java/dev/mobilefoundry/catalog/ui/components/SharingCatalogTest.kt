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

class SharingCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun family(text: String) { compose.onNodeWithContentDescription("Component families").performScrollTo(); click(text) }
    private fun choose(title: String, option: String) {
        compose.onNodeWithContentDescription(title).performScrollTo().performClick()
        compose.onNode(hasText(option) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }
    private fun confirm(text: String) { compose.onNode(hasText(text) and hasClickAction()).performClick() }
    @Test fun membershipRolesAndLinkChoiceSurviveRecreationRoutesAndThemesWithoutRestoringRemoval() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce"); compose.onNodeWithContentDescription("Increase Pocket notebook").performScrollTo().performClick()
        family("Sharing"); click("Open sharing preview")
        compose.onNodeWithText("Remove Alex Morgan").performScrollTo().assertIsNotEnabled().performClick()
        choose("Invite as", "Editor"); click("Use River address"); click("Add preview member")
        compose.onNodeWithText("4 members").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Access for River Vale").performScrollTo().assertTextContains("Editor")
        choose("Access for Jamie Park", "Viewer")
        choose("Link access", "Off")
        compose.onNodeWithText("Copy workspace link").performScrollTo().assertIsNotEnabled().performClick()
        click("Remove River Vale"); confirm("Keep member")
        compose.onNodeWithText("4 members").performScrollTo().assertExists()
        click("Remove River Vale")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Remove preview member").assertDoesNotExist()
        compose.onNodeWithText("4 members").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Access for Jamie Park").performScrollTo().assertTextContains("Viewer")
        compose.onNodeWithContentDescription("Access for River Vale").performScrollTo().assertTextContains("Editor")
        compose.onNodeWithContentDescription("Link access").performScrollTo().assertTextContains("Off")
        click("Remove River Vale"); confirm("Remove preview member")
        compose.onNodeWithText("3 members").performScrollTo().assertExists()
        compose.onNodeWithText("Invites: 1 · Role changes: 1 · Removals: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Sharing")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open sharing preview")
        compose.onNodeWithContentDescription("Access for Jamie Park").performScrollTo().assertTextContains("Viewer")
        compose.onNodeWithText("Link access is off").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
    @Test fun invitationErrorsStaleRemovalAndPendingCommandsPreserveMembershipAndCopyAdmission() {
        var values by mutableStateOf(SharingValues())
        val copied = mutableListOf<String>()
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { SharingContent(values, { values = it }, copyText = { copied += it }) } } }
        val initial = values
        compose.runOnIdle { values = values.changeRole("alex", MemberRole.EDITOR).changeRole("missing", MemberRole.EDITOR) }
        assertEquals(initial, values); assertNull(values.removal("alex"))
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("unknown@example.test")
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals("Use river@example.test in this preview.", values.error)
        compose.runOnIdle { values = values.editDraft("lena@example.test").invite() }
        assertEquals("This teammate is unavailable.", values.error)
        compose.runOnIdle { values = values.editDraft(" RIVER@EXAMPLE.TEST \n").chooseInviteRole(MemberRole.EDITOR).invite() }
        assertEquals(MemberRole.EDITOR, values.role("river")); assertEquals(1, values.invites); assertTrue(values.draft.isEmpty())
        compose.runOnIdle { values = values.editDraft("river@example.test").invite() }
        assertEquals("Already a member.", values.error)
        val stale = requireNotNull(values.removal("jamie"))
        click("Remove Jamie Park")
        compose.runOnIdle { values = values.changeRole("jamie", MemberRole.VIEWER).remove(stale) }
        compose.onNodeWithText("Remove preview member").assertDoesNotExist()
        assertTrue("jamie" in values.members); assertEquals(0, values.removals)
        click("Copy workspace link"); assertEquals(listOf(SharingValues.LINK), copied)
        click("Show pending access update")
        compose.onNodeWithText("Copy workspace link").performScrollTo().assertIsNotEnabled().performClick()
        val pending = values; val request = MemberRemoval("sam", values.revision)
        compose.runOnIdle { values = values.editDraft("").chooseInviteRole(MemberRole.VIEWER).invite().changeRole("sam", MemberRole.EDITOR).remove(request).chooseLinkAccess(LinkAccess.OFF).copyLink().resetMembers() }
        assertEquals(pending, values); assertEquals(1, copied.size)
        click("Show pending access update"); click("Remove Sam Chen")
        compose.runOnIdle { values = values.copy(enabled = false) }
        compose.onNodeWithText("Remove preview member").assertDoesNotExist()
        val disabled = values
        compose.runOnIdle { values = values.editDraft("").chooseInviteRole(MemberRole.VIEWER).invite().changeRole("sam", MemberRole.EDITOR).remove(request).chooseLinkAccess(LinkAccess.EDIT).copyLink().resetMembers() }
        assertEquals(disabled, values)
        compose.runOnIdle { values = values.copy(enabled = true).chooseLinkAccess(LinkAccess.OFF) }
        compose.onNodeWithText("Copy workspace link").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(1, values.copies); assertEquals(1, copied.size)
        compose.runOnIdle { values = values.resetMembers().remove(request) }
        assertEquals(listOf("alex", "jamie", "sam"), values.members)
        assertEquals(MemberRole.EDITOR, values.role("jamie")); assertEquals(LinkAccess.OFF, values.linkAccess)
    }
}
