package dev.mobilefoundry.ui.components.forms.select

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun <Value> SelectField(title: String, options: List<Value>, selected: Value, onSelect: (Value) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, label: (Value) -> String) {
    require(options.isNotEmpty() && options.distinct().size == options.size && selected in options)
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.inline)) {
        Text(title, style = FoundryTheme.tokens.typography.label)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = title }) {
                Text(label(selected))
                Text(" ▾", modifier = Modifier.clearAndSetSemantics {})
            }
            DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false; onSelect(option)
                }) }
            }
        }
    }
}
