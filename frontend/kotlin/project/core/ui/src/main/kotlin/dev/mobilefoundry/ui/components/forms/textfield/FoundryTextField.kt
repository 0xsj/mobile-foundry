package dev.mobilefoundry.ui.components.forms.textfield

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics

/** Native single-line input. Focus, blur validation and keyboard policy belong to the caller. */
@Composable
fun FoundryTextField(
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
