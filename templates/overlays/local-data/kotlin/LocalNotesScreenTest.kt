package dev.mobilefoundry.product

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class LocalNotesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun nativeFormsObservationConfirmationAndRecreationUseDeviceStorage() {
        val original = "Starter ${UUID.randomUUID().toString().take(8)}"
        val updated = "Edited ${original.takeLast(8)}"
        compose.onNode(hasText("Library") and isSelectable()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Live collection:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val baseline = compose.onNode(hasText("Live collection:", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.split(" ")[2].toInt()
        click("Create note"); compose.onNodeWithText("Error: Enter a title.").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement(original)
        click("Create note"); awaitText(original); awaitText("Live collection: ${baseline + 1} notes")
        compose.onNodeWithContentDescription("Edit $original").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(updated)
        click("Save changes"); awaitText(updated)
        compose.onNodeWithContentDescription("Delete $updated").performScrollTo().performClick()
        compose.onNodeWithText("Keep note").performClick(); compose.onNodeWithText(updated).assertExists()
        compose.activityRule.scenario.recreate(); awaitText(updated); awaitText("Live collection: ${baseline + 1} notes")
        compose.onNodeWithContentDescription("Delete $updated").performScrollTo().performClick()
        compose.onNodeWithText("Delete note").performClick()
        awaitText("Note deleted."); awaitText("Live collection: $baseline notes")
        compose.onNodeWithText(updated).assertDoesNotExist()
    }
}
