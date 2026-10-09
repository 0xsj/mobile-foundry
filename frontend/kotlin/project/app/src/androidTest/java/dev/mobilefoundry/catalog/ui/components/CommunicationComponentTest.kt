package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.messagebubble.MessageBubble
import dev.mobilefoundry.ui.components.feedback.typing.TypingIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.attachmentrow.AttachmentRow
import dev.mobilefoundry.ui.components.patterns.messagecomposer.MessageComposer
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommunicationComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun largeTextKeepsPassiveArtworkSeparateFromActionsAndBusyComposerGatesSlots() {
        var busy by mutableStateOf(false)
        var actions = 0
        var sends = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FoundryTheme {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        MessageBubble("Jordan", "Longer reply text wraps with native font scaling.", "Saved locally") {
                            AttachmentRow("A longer attachment filename.pdf", "PDF · 240 KB", preview = { Text("Passive file artwork") }, actions = {
                                ActionButton({ actions++ }) { Text("Inspect attachment") }
                            })
                        }
                        TypingIndicator("Jordan is typing…", animated = false)
                        MessageComposer("Reply", rememberTextFieldState("Keep this text"), "Send reply", true, { sends++ },
                            isSending = busy, actions = { interactive ->
                                ActionButton({ actions++ }, enabled = interactive) { Text("Attach another file") }
                            })
                    }
                }
            }
        }
        compose.onNodeWithText("Passive file artwork").assertDoesNotExist()
        compose.onNodeWithContentDescription("Jordan is typing…").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithText("Inspect attachment").performScrollTo().performClick()
        compose.onNodeWithText("Attach another file").performScrollTo().performClick()
        compose.onNodeWithText("Send reply").performScrollTo().performClick()
        assertEquals(2, actions); assertEquals(1, sends)
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Reply").performScrollTo().assertIsNotEnabled().assertTextContains("Keep this text")
        compose.onNodeWithText("Attach another file").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Send reply").performScrollTo().assertIsNotEnabled()
    }
}
