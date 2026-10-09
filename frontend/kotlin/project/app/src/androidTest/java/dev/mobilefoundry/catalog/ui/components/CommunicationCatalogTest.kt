package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class CommunicationCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun open() { click("Communication"); click("Design room") }
    private fun add(inPreview: Boolean = true) {
        val node = compose.onNodeWithContentDescription("Add preview attachment")
        if (!inPreview) node.performScrollTo()
        node.performClick()
    }
    private fun draft() = compose.onNodeWithText("Message draft")
    @Test fun transferFailureRetryPauseAndSendPreserveTheDraft() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        open()
        compose.onNodeWithText("Send preview").assertIsNotEnabled()
        draft().performTextInput("A multiline\nstudy brief")
        add()
        compose.onNodeWithText("Send preview").assertIsNotEnabled()
        click("Start transfer preview"); click("Fail transfer preview")
        draft().assertTextContains("A multiline\nstudy brief")
        compose.onNodeWithText("Transfer failed. Your draft is still here.").assertExists()
        click("Retry transfer preview"); click("Pause transfer preview")
        compose.onNodeWithText("Send preview").assertIsNotEnabled()
        click("Resume transfer preview"); click("Finish transfer preview")
        compose.onNodeWithText("Send preview").performClick()
        draft().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Send preview").assertIsNotEnabled()
        compose.onNodeWithText("A multiline\nstudy brief").assertExists()
        click("Inspect sent attachment")
        compose.onNodeWithText("Sent previews: 1 · Inspected files: 1").performScrollTo().assertIsDisplayed()
    }
    @Test fun routeThemeAndSavedStateKeepDraftAndFailedAttachment() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        open(); draft().performTextInput("Keep this reply")
        add(); click("Start transfer preview"); click("Fail transfer preview")
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Communication")
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        click("Design room")
        restoration.emulateSavedInstanceStateRestore()
        draft().assertTextContains("Keep this reply")
        compose.onNodeWithText("Transfer failed. Your draft is still here.").assertExists()
        click("Cancel transfer preview")
        draft().assertTextContains("Keep this reply")
        compose.onNodeWithContentDescription("Remove draft attachment").assertDoesNotExist()
        compose.onNodeWithText("Send preview").assertIsEnabled().performClick()
        compose.onNodeWithText("Sent previews: 1 · Inspected files: 0").performScrollTo().assertIsDisplayed()
    }
    @Test fun disabledPreviewBlocksInputAttachmentAndSend() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Communication")
        draft().performScrollTo().performTextInput("Keep this draft")
        add(inPreview = false); click("Enable communication controls")
        draft().performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Send preview").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove draft attachment").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Start transfer preview").performScrollTo().assertIsNotEnabled()
        click("Enable communication controls"); click("Design room")
        click("Cancel transfer preview")
        draft().assertTextContains("Keep this draft")
    }
}
