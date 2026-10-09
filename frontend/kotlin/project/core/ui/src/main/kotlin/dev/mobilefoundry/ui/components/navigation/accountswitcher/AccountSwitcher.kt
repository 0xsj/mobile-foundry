package dev.mobilefoundry.ui.components.navigation.accountswitcher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

data class AccountOption(val id: String, val title: String, val detail: String? = null, val enabled: Boolean = true)

/** Native menu for supplied identities. Unknown selection displays the placeholder without repairing state. */
@Composable
fun AccountSwitcher(title: String, options: List<AccountOption>, selection: String?, placeholder: String,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    require(options.map { it.id }.distinct().size == options.size)
    var expanded by remember(enabled, options.isEmpty()) { mutableStateOf(false) }
    val t = FoundryTheme.tokens
    val current = options.firstOrNull { it.id == selection }
    Box(modifier) {
        ActionButton({ expanded = true }, Modifier.semantics { contentDescription = title; stateDescription = current?.title ?: placeholder },
            variant = ButtonVariant.SECONDARY, enabled = enabled && options.isNotEmpty()) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, style = t.typography.caption)
                Text(current?.title ?: placeholder, style = t.typography.label)
            }
            Text(" ⌄", Modifier.clearAndSetSemantics {})
        }
        DropdownMenu(expanded && enabled && options.isNotEmpty(), onDismissRequest = { expanded = false }) {
            options.forEach { option -> key(option.id) {
                DropdownMenuItem(text = {
                    Column {
                        Text(option.title)
                        option.detail?.let { Text(it, style = t.typography.caption) }
                    }
                }, modifier = Modifier.semantics { selected = option.id == selection }, enabled = option.enabled,
                    trailingIcon = { if (option.id == selection) Text("✓", Modifier.clearAndSetSemantics {}) },
                    onClick = {
                        expanded = false
                        if (enabled && option.enabled && option.id != selection) onSelect(option.id)
                    })
            } }
        }
    }
}
