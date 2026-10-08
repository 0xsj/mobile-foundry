package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.fieldgroup.FieldGroup
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField

@Composable
internal fun FieldGroupExample(title: String, onTitle: (String) -> Unit, owner: String, onOwner: (String) -> Unit,
    validated: Boolean, onValidate: () -> Unit) {
    val focus = LocalFocusManager.current
    val invalid = title.isBlank() || owner.isBlank()
    Card {
        FieldGroup("Workspace naming", help = "Both names stay in this gallery.",
            error = if (validated && invalid) "Enter both workspace and owner names." else null) {
            LabeledTextField("Workspace title", title, onTitle, Modifier.fillMaxWidth())
            LabeledTextField("Owner name", owner, onOwner, Modifier.fillMaxWidth())
        }
        ActionButton({ onValidate(); focus.clearFocus() }, variant = ButtonVariant.SECONDARY) { Text("Validate fields") }
    }
}
