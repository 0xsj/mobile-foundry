package dev.mobilefoundry.ui.components.forms.multiline

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics

/** Native growing input with bounded visible lines; no character truncation or validation rules. */
@Composable
fun MultilineField(title: String, state: TextFieldState, modifier: Modifier = Modifier,
    help: String? = null, error: String? = null, enabled: Boolean = true, lines: IntRange = 3..6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, onKeyboardAction: KeyboardActionHandler? = null) {
    require(!lines.isEmpty() && lines.first > 0)
    OutlinedTextField(state, modifier.semantics { if (error != null) error(error) }, enabled = enabled,
        label = { Text(title) }, isError = error != null,
        supportingText = if (error != null || help != null) {
            { Text(if (error != null) "Error: $error" else help.orEmpty()) }
        } else null,
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = lines.first, maxHeightInLines = lines.last),
        keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction)
}
