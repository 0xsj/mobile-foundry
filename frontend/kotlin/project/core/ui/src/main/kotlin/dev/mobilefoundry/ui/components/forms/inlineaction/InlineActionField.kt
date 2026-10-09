package dev.mobilefoundry.ui.components.forms.inlineaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Controlled draft with one guarded button/Done action. No parsing, validation, clearing or request. */
@Composable
fun InlineActionField(title: String, value: String, onValueChange: (String) -> Unit, actionLabel: String, canSubmit: Boolean,
    onSubmit: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isBusy: Boolean = false,
    help: String? = null, error: String? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    val t = FoundryTheme.tokens
    val interactive = enabled && !isBusy
    val submit = { if (interactive && canSubmit) onSubmit() }
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(value, { if (interactive) onValueChange(it) }, fieldModifier.semantics { if (error != null) error(error) },
            label = { Text(title) }, singleLine = true, enabled = interactive, isError = error != null,
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
    }
    val action: @Composable () -> Unit = {
        ActionButton(submit, variant = ButtonVariant.SECONDARY, isBusy = isBusy, enabled = enabled && canSubmit) { Text(actionLabel) }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 400.dp || LocalDensity.current.fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(t.space.inline)) { field(Modifier.fillMaxWidth()); action() }
            } else Row(horizontalArrangement = Arrangement.spacedBy(t.space.inline), verticalAlignment = Alignment.CenterVertically) { field(Modifier.weight(1f)); action() }
        }
        if (error != null) Text("Error: $error", style = t.typography.caption, color = t.colors.crit.color)
        else if (help != null) Text(help, style = t.typography.caption, color = t.colors.inkSecondary.color)
    }
}
