package dev.mobilefoundry.ui.components.forms.searchfield

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

/** Caller owns filtering, debounce and requests. */
@Composable
fun SearchField(title: String, value: String, onValueChange: (String) -> Unit, clearLabel: String,
                modifier: Modifier = Modifier, onSubmit: () -> Unit = {}, enabled: Boolean = true) {
    OutlinedTextField(value, onValueChange, modifier, label = { Text(title) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (enabled) onSubmit() }), enabled = enabled,
        trailingIcon = if (value.isNotEmpty()) ({ TextButton(onClick = { if (enabled) onValueChange("") }, enabled = enabled) { Text(clearLabel) } }) else null)
}
