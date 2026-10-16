package dev.mobilefoundry.catalog.ui.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.catalog.MainActivity
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class LiveNotesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun open() {
        compose.onNode(hasText("Studio") and hasClickAction()).performClick()
        compose.onNodeWithText("Open catalog").performClick(); click("Live notes")
    }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text != "Back") node.performScrollTo(); node.performClick()
    }
    private fun waitFor(text: String) { compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun source(text: String) {
        compose.onNodeWithContentDescription("Storage").performScrollTo().performClick()
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }
    private fun title(value: String) {
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("Live note title") and hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Live note title") and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }
    @Test fun memoryReadersFollowCRUDAndPausedReaderResumesCurrentData() {
        open(); source("Memory")
        waitFor("First reader: 0 notes"); waitFor("Second reader: 0 notes")
        title("Live idea"); click("Create live note"); waitFor("First reader: Live idea"); waitFor("Second reader: Live idea")
        click("Pause second reader")
        compose.onNodeWithContentDescription("Edit live note Live idea").performScrollTo().performClick()
        title("Renamed idea"); click("Save live note"); waitFor("First reader: Renamed idea")
        compose.onNodeWithText("Second reader: Live idea").assertExists()
        compose.onNodeWithText("Second reader: Renamed idea").assertDoesNotExist()
        click("Resume second reader"); waitFor("Second reader: Renamed idea")
        compose.onNodeWithContentDescription("Delete live note Renamed idea").performScrollTo().performClick()
        waitFor("First reader: 0 notes"); waitFor("Second reader: 0 notes")
    }
    @Test fun memorySourceSurvivesBranchesAndActivityRecreationButClosesWithRoute() {
        open(); source("Memory")
        title("Retained live idea"); click("Create live note"); waitFor("First reader: Retained live idea")
        click("Pause second reader"); source("Device storage"); source("Memory")
        waitFor("First reader: Retained live idea"); compose.onNodeWithText("Resume second reader").assertExists()
        compose.activityRule.scenario.recreate()
        waitFor("First reader: Retained live idea"); compose.onNodeWithText("Resume second reader").assertExists()
        click("Resume second reader"); waitFor("Second reader: Retained live idea")
        click("Back"); click("Live notes"); source("Memory")
        waitFor("First reader: 0 notes"); waitFor("Second reader: 0 notes")
        compose.onNodeWithText("First reader: Retained live idea").assertDoesNotExist()
    }
    @Test fun deviceReadersAutomaticallyReloadWhenRouteReopensAndStaySeparateFromMemory() {
        val name = "Device live ${UUID.randomUUID()}"
        open(); title(name); click("Create live note")
        waitFor("First reader: $name"); waitFor("Second reader: $name")
        click("Back"); click("Live notes")
        waitFor("First reader: $name"); waitFor("Second reader: $name")
        source("Memory"); waitFor("First reader: 0 notes")
        compose.onNodeWithText("First reader: $name").assertDoesNotExist()
        source("Device storage"); waitFor("First reader: $name")
        compose.onNodeWithContentDescription("Delete live note $name").performScrollTo().performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("First reader: $name").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithText("Second reader: $name").fetchSemanticsNodes().isEmpty() }
    }
}
