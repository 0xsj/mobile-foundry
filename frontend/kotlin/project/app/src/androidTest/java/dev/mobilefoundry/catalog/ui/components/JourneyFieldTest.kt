package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.components.forms.multiline.MultilineField
import dev.mobilefoundry.ui.components.forms.password.PasswordField
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JourneyFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeFieldsPreferErrorAndKeepLongMultilineTextWithoutTruncation() {
        val password = TextFieldState()
        val note = TextFieldState()
        var error by mutableStateOf<String?>("Please review")
        compose.setContent { FoundryTheme { Column {
            PasswordField("Secret", password, help = "Password help", error = error)
            MultilineField("Long note", note, help = "Note help", error = error, lines = 2..3)
        } } }
        compose.onNode(hasText("Secret") and hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Please review"))
        compose.onNodeWithText("Password help").assertDoesNotExist()
        compose.runOnIdle { error = null }
        compose.onNodeWithText("Password help").assertIsDisplayed()
        val longNote = (1..20).joinToString("\n") { "Line $it remains in the draft" }
        compose.onNodeWithText("Long note").performTextInput(longNote)
        compose.runOnIdle { assertEquals(longNote, note.text.toString()) }
    }
}
