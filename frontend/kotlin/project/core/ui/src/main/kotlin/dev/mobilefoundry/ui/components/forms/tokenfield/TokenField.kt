package dev.mobilefoundry.ui.components.forms.tokenfield

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Caller-owned draft/tokens. Admission, parsing, duplicates and clearing are external.
 * Slot controls must consume interactivity; a disabled draft may retain its caller value. */
@Composable
fun TokenField(title: String, value: String, onValueChange: (String) -> Unit, addLabel: String,
    canAdd: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    isBusy: Boolean = false, help: String? = null, error: String? = null,
    tokens: @Composable FlowRowScope.(interactive: Boolean) -> Unit) {
    val interactive = enabled && !isBusy
    val add = { if (interactive && canAdd) onAdd() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.inline)) {
        WrapLayout { tokens(interactive) }
        LabeledTextField(title, value, onValueChange, Modifier.fillMaxWidth(), help = help, error = error, enabled = interactive,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }))
        ActionButton(add, variant = ButtonVariant.SECONDARY, isBusy = isBusy, enabled = enabled && canAdd) { Text(addLabel) }
    }
}
