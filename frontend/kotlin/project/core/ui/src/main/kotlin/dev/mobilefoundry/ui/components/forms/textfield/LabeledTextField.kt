package dev.mobilefoundry.ui.components.forms.textfield

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics

/** Native single-line input. Focus, blur validation and keyboard policy belong to the caller. */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = modifier.semantics { if (error != null) error(error) },
        label = { Text(label) }, enabled = enabled, singleLine = true, isError = error != null,
        supportingText = if (error != null || help != null) {
            { Text(if (error != null) "Error: $error" else help.orEmpty()) }
        } else null,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
}

/** State-based native input for consumers that need synchronous IME editing state. */
@Composable
fun LabeledTextField(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    help: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
) {
    OutlinedTextField(state = state,
        modifier = modifier.semantics { if (error != null) error(error) },
        label = { Text(label) }, enabled = enabled, isError = error != null,
        supportingText = if (error != null || help != null) {
            { Text(if (error != null) "Error: $error" else help.orEmpty()) }
        } else null,
        lineLimits = TextFieldLineLimits.SingleLine, inputTransformation = inputTransformation,
        keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction)
}
