package dev.mobilefoundry.catalog.ui.notes

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.catalog.composition.LocalNotesProvider
import dev.mobilefoundry.data.SQLiteNotesRepository
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalNotesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text == "Back") node.performClick() else node.performScrollTo().performClick()
    }
    private fun editable() {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Title") and hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun title(text: String) { editable(); compose.onNode(hasText("Title") and hasSetTextAction()).performScrollTo().performTextReplacement(text) }
    private fun saved(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Edit $text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Edit $text").performScrollTo().assertExists()
    }
    private fun source(label: String) {
        editable(); compose.onNodeWithContentDescription("Storage").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isPopup())).performClick(); editable()
    }
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test fun rapidComposeKeyInputReachesTheSavedCommandWithoutDroppingCharacters() {
        val model = LocalNotesViewModel(InMemoryNotesRepository()) { throw AssertionError(it) }
        compose.setContent {
            val state by model.state.collectAsState()
            val inputMode = LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            LaunchedEffect(model) { model.refresh() }
            FoundryTheme { LocalNotesContent(state, model, LocalNotesProvider.MEMORY, {}) }
        }
        editable()
        val field = compose.onNode(hasText("Title") and hasSetTextAction())
        field.performScrollTo().requestFocus().assertIsFocused()
        val typed = "rapidnotes".repeat(3)
        // One burst through the Compose key pipeline; OS/IME window routing is separate evidence.
        field.performKeyInput {
            repeat(3) { listOf(Key.R, Key.A, Key.P, Key.I, Key.D, Key.N, Key.O, Key.T, Key.E, Key.S).forEach { pressKey(it) } }
        }
        compose.waitUntil(5_000) { model.state.value.title == typed }
        field.assertTextContains(typed).performImeAction()
        saved(typed)
    }
    @Test fun deviceCRUDSurvivesRouteRemovalAndMemoryUsesTheSameScreen() {
        val suffix = UUID.randomUUID().toString().take(8)
        val first = "Device idea $suffix"; val second = "Second idea $suffix"; val renamed = "Renamed idea $suffix"
        compose.setContent { FoundryTheme { MainNavigation() } }
        click("Local notes"); editable(); click("Create note")
        compose.onNodeWithText("Error: Enter a title.").performScrollTo().assertExists()
        title(first); click("Create note"); saved(first)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = runBlocking { (SQLiteNotesRepository(context).list() as Outcome.Ok).value.first { it.title == first }.id }
        title(second); click("Create note"); saved(second)
        compose.onNodeWithContentDescription("Edit $first").performScrollTo().performClick()
        title(renamed); click("Save changes"); saved(renamed)
        assertEquals(id, runBlocking { (SQLiteNotesRepository(context).list() as Outcome.Ok).value.first { it.title == renamed }.id })
        click("Back"); click("Local notes"); editable(); saved(renamed); saved(second)
        compose.onNode(hasText("Title") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        source("Memory"); compose.onNodeWithText("No saved notes yet.").performScrollTo().assertExists()
        title("Memory idea $suffix"); click("Create note"); saved("Memory idea $suffix")
        source("Device storage"); saved(renamed); saved(second)
        compose.onNodeWithContentDescription("Edit Memory idea $suffix").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete $renamed").performScrollTo().performClick()
        compose.onNodeWithText("Keep note").performClick(); saved(renamed)
        compose.onNodeWithContentDescription("Delete $renamed").performScrollTo().performClick()
        compose.onNodeWithText("Delete note").performClick(); editable()
        compose.onNodeWithContentDescription("Edit $renamed").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete $second").performScrollTo().performClick()
        compose.onNodeWithText("Delete note").performClick(); editable()
        click("Back"); click("Local notes"); editable()
        compose.onNodeWithContentDescription("Edit $renamed").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit $second").assertDoesNotExist()
    }
    @Test fun initialReadFailureBlocksWritingAndWriteFailurePreservesDraftForExplicitRetry() {
        val base = InMemoryNotesRepository(); var failRead = true; var failWrite = true; var creates = 0; var defects = 0
        val repo = object : NotesRepository {
            override suspend fun list(): AppResult<List<Note>> = if (failRead) { failRead = false; Outcome.Err(Failure.Unavailable(FailureMeta("Read temporarily unavailable", "notes.storage_unavailable"))) } else base.list()
            override suspend fun create(command: CreateNote): AppResult<Note> {
                creates++; return if (failWrite) { failWrite = false; Outcome.Err(Failure.Unavailable(FailureMeta("Write temporarily unavailable", "notes.storage_unavailable"))) } else base.create(command)
            }
            override suspend fun update(command: UpdateNote) = base.update(command)
            override suspend fun delete(command: DeleteNote) = base.delete(command)
        }
        val model = LocalNotesViewModel(repo) { defects++ }
        compose.setContent {
            val state by model.state.collectAsState()
            LaunchedEffect(model) { model.refresh() }
            FoundryTheme { LocalNotesContent(state, model, LocalNotesProvider.DEVICE, {}) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Read temporarily unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create note").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(0, creates); click("Retry"); editable()
        title("Preserved draft"); click("Create note")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Error: Write temporarily unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Title") and hasSetTextAction()).performScrollTo().assertTextContains("Preserved draft")
        assertEquals(1, creates); click("Create note"); saved("Preserved draft")
        assertEquals(2, creates); assertEquals(0, defects)
    }
}
