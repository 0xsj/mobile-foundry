package dev.mobilefoundry.catalog.ui.forms

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.ui.components.feedback.mutation.MutationFeedback
import dev.mobilefoundry.ui.styles.tokens.FoundryAppearance
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class FormsCatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun field() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
    private fun submit() = compose.onNode(hasText("Create note") and hasClickAction())
    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test fun validationProviderSwapImeConfirmationAndResetUseTheSameForm() {
        compose.setContent { FoundryCatalogTheme { FormsCatalogScreen(onBack = {}) } }
        submit().performScrollTo().performClick()
        field().assertIsFocused()
        waitFor("Error: Enter a title.")
        compose.onNodeWithText("HTTP").performScrollTo().performClick()
        waitFor("Error: Enter a title.")
        for (provider in listOf("Memory", "HTTP")) {
            compose.onNodeWithText(provider).performScrollTo().performClick()
            field().performScrollTo().performTextReplacement("Native draft")
            field().performImeAction()
            waitFor("Note created")
            field().assertIsNotEnabled()
            submit().assertIsNotEnabled()
            compose.onNodeWithText("New note").performScrollTo().performClick()
            field().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            field().assertIsEnabled()
            compose.onNodeWithText("Note created").assertDoesNotExist()
        }
    }

    @Test fun bothProvidersRetainDraftForRefusalsAndRedactInternalDetails() {
        compose.setContent { FoundryCatalogTheme { FormsCatalogScreen(onBack = {}) } }
        for (provider in listOf("Memory", "HTTP")) {
            compose.onNodeWithText(provider).performScrollTo().performClick()
            field().performScrollTo().performTextReplacement("Keep this draft")
            for ((scenario, message) in listOf(
                "Field error" to "Check the highlighted field.",
                "Unavailable" to "Notes are temporarily unavailable.",
                "Timeout" to "No confirmation was received.",
                "Malformed" to "An unexpected error occurred.",
            )) {
                compose.onNodeWithText(scenario).performScrollTo().performClick()
                submit().performScrollTo().performClick()
                waitFor("Error: $message")
                field().assertTextContains("Keep this draft")
                if (scenario == "Field error") waitFor("Error: This title is reserved.")
                if (scenario != "Field error") waitFor("We couldn’t confirm whether the note was saved. Check your notes before submitting again.")
                compose.onNodeWithText("Private malformed create detail.").assertDoesNotExist()
            }
            compose.onNodeWithText("Success").performScrollTo().performClick()
            submit().performScrollTo().performClick()
            waitFor("Note created")
            compose.onNodeWithText("New note").performScrollTo().performClick()
        }
    }

    @Test fun pendingWriteDisablesNativeControlsAndButtonImeShareOneInvocation() {
        var calls = 0
        val gate = CompletableDeferred<AppResult<Note>>()
        val model = CreateNoteViewModel(NoteCreator { calls++; gate.await() }) { throw it }
        compose.setContent {
            FoundryCatalogTheme {
                val state by model.state.collectAsState()
                Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
                    CreateNoteScreen(state, model::editTitle, model::blurTitle,
                        submit = { model.submit() != null }, reset = model::reset)
                }
            }
        }
        field().performTextInput("Once")
        field().performImeAction()
        waitFor("Waiting for confirmation…")
        field().assertIsNotEnabled()
        compose.onNodeWithText("Creating note…").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Clear form").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); gate.complete(Outcome.Ok(Note("created", "Once"))) }
        waitFor("Note created")
    }

    @Test fun feedbackProjectsFailuresAndUsesTheCallerSuccessSlot() {
        compose.setContent {
            FoundryCatalogTheme {
                Column {
                    MutationFeedback<Note>(MutationState.Idle, "Hidden") { Text("Hidden receipt") }
                    MutationFeedback<Note>(MutationState.Submitting, "Saving") { Text("Hidden receipt") }
                    MutationFeedback(MutationState.Succeeded(Note("one", "Receipt")), "Hidden") { Text(it.title) }
                    MutationFeedback<Note>(MutationState.Failed(Failure.Internal(FailureMeta("Secret detail", "secret"))), "Hidden") { Text("Hidden receipt") }
                }
            }
        }
        compose.onNodeWithText("Saving").assertIsDisplayed()
        compose.onNodeWithText("Receipt").assertIsDisplayed()
        compose.onNodeWithText("Error: An unexpected error occurred.").assertIsDisplayed()
        compose.onNodeWithText("Secret detail").assertDoesNotExist()
        compose.onNodeWithText("Hidden receipt").assertDoesNotExist()
    }

    @Test fun formPanelsActuallySampleGlassAndReductionRestoresSolidWithoutLosingReceipt() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val style = mutableStateOf(FoundryThemeStyle.SOLID)
        val reduced = mutableStateOf(false)
        compose.setContent {
            FoundryTheme(appearance = FoundryAppearance.LIGHT, style = style.value, reduceTransparency = reduced.value) {
                FormsCatalogScreen(onBack = {})
            }
        }
        field().performScrollTo().performTextInput("Theme survives")
        field().performImeAction()
        waitFor("Note created")
        val panel = compose.onNodeWithTag("create-note-panel")
        panel.performScrollTo()
        fun sample() = panel.captureToImage().toPixelMap().let { it[it.width / 2, 8] }
        val solid = sample()
        compose.runOnIdle { style.value = FoundryThemeStyle.GLASS }
        val glass = sample()
        assertTrue("The actual form must change material: $solid / $glass",
            maxOf(abs(solid.red - glass.red), abs(solid.green - glass.green), abs(solid.blue - glass.blue)) > .008f)
        field().assertTextContains("Theme survives")
        compose.onNodeWithText("Note created").assertExists()
        compose.runOnIdle { reduced.value = true }
        val opaque = sample()
        assertEquals(solid.red, opaque.red, .004f)
        assertEquals(solid.green, opaque.green, .004f)
        assertEquals(solid.blue, opaque.blue, .004f)
        compose.runOnIdle { reduced.value = false; style.value = FoundryThemeStyle.SOLID }
        field().assertTextContains("Theme survives")
        compose.onNodeWithText("New note").assertExists()
    }
}
