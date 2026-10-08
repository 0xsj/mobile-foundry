package dev.mobilefoundry.ui.components.navigation.tabbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurface
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurfaceRole

data class FoundryTabItem(val id: String, val label: String, val icon: @Composable () -> Unit)

/** Values and callbacks only. The host supplies safe-area padding and a glass backdrop. */
@Composable
fun FoundryTabBar(
    items: List<FoundryTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FoundrySurface(modifier, role = FoundrySurfaceRole.FLOATING) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)) {
            items.forEach { item ->
                NavigationBarItem(selected = item.id == selectedId,
                    onClick = { onSelect(item.id) }, icon = item.icon,
                    label = { Text(item.label) })
            }
        }
    }
}
