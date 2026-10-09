package dev.mobilefoundry.ui.components.navigation.tabbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole

data class TabItem(val id: String, val label: String, val icon: @Composable () -> Unit)

/** 1..5 unique nonempty IDs including selectedId. Values/callbacks only; host supplies insets/backdrop and content. */
@Composable
fun TabBar(
    items: List<TabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.size in 1..5 && items.all { it.id.isNotEmpty() } && items.map { it.id }.distinct().size == items.size)
    require(items.any { it.id == selectedId })
    Surface(modifier, role = SurfaceRole.FLOATING) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)) {
            items.forEach { item ->
                NavigationBarItem(selected = item.id == selectedId,
                    onClick = { if (item.id != selectedId) onSelect(item.id) }, icon = item.icon,
                    label = { Text(item.label) })
            }
        }
    }
}
