package dev.mobilefoundry.ui.components.overlays.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

data class MenuAction(val id: String, val title: String, val destructive: Boolean = false,
    val enabled: Boolean = true, val onSelect: () -> Unit)

@Composable
fun ActionMenu(title: String, actions: List<MenuAction>, modifier: Modifier = Modifier) {
    require(actions.map { it.id }.distinct().size == actions.size)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = actions.isNotEmpty()) { Text(title) }
        DropdownMenu(expanded && actions.isNotEmpty(), onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.title) }, enabled = action.enabled,
                    colors = MenuDefaults.itemColors(textColor = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface),
                    onClick = { expanded = false; action.onSelect() })
            }
        }
    }
}
