package dev.mobilefoundry.ui.components.forms.radiogroup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun <Value> RadioGroup(title: String, options: List<Value>, selected: Value, onSelect: (Value) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, label: (Value) -> String) {
    require(options.isNotEmpty() && options.distinct().size == options.size && selected in options)
    val t = FoundryTheme.tokens
    Column(modifier.selectableGroup()) {
        Text(title, style = t.typography.label)
        options.forEach { option ->
            Row(Modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive)
                .selectable(selected == option, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
                RadioButton(selected == option, onClick = null, enabled = enabled)
                Text(label(option), modifier = Modifier.weight(1f))
            }
        }
    }
}
