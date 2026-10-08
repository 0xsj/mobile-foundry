package dev.mobilefoundry.ui.components.forms.password

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType

enum class PasswordPurpose { CURRENT, NEW }

/** Native obscured input. The caller owns TextFieldState, validation and submission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordField(title: String, state: TextFieldState, modifier: Modifier = Modifier,
    help: String? = null, error: String? = null, enabled: Boolean = true,
    purpose: PasswordPurpose = PasswordPurpose.CURRENT,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    onKeyboardAction: KeyboardActionHandler? = null) {
    OutlinedSecureTextField(state, modifier.semantics {
        contentType = if (purpose == PasswordPurpose.NEW) ContentType.NewPassword else ContentType.Password
        if (error != null) error(error)
    }, enabled = enabled, label = { Text(title) }, isError = error != null,
        supportingText = if (error != null || help != null) {
            { Text(if (error != null) "Error: $error" else help.orEmpty()) }
        } else null,
        textObfuscationMode = TextObfuscationMode.Hidden,
        keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction)
}
