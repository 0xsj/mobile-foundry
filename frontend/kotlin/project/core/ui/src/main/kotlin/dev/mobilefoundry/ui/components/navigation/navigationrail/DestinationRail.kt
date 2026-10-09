package dev.mobilefoundry.ui.components.navigation.navigationrail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

data class RailDestination(val id: String, val label: String, val accessibilityLabel: String = label, val enabled: Boolean = true)

/** Supplied destinations with passive icons. Host owns width, routes and the compact alternative. */
@Composable
fun DestinationRail(title: String, destinations: List<RailDestination>, selection: String?, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, icon: @Composable (RailDestination) -> Unit) {
    require(destinations.map { it.id }.distinct().size == destinations.size)
    val t = FoundryTheme.tokens
    val itemHeight = 80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(modifier, role = SurfaceRole.FLOATING) {
        NavigationRail(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), containerColor = Color.Transparent,
            contentColor = t.colors.ink.color, header = {
                Text(title, Modifier.padding(t.space.inline).semantics { heading() }, style = t.typography.caption)
            }) {
            destinations.forEach { destination -> key(destination.id) {
                NavigationRailItem(selected = selection == destination.id, onClick = { onSelect(destination.id) }, enabled = destination.enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = itemHeight).semantics { contentDescription = destination.accessibilityLabel },
                    icon = { Box(Modifier.clearAndSetSemantics {}) { icon(destination) } },
                    label = { Text(destination.label, style = t.typography.caption) }, alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(selectedIconColor = t.colors.accent.color,
                        selectedTextColor = t.colors.accent.color, indicatorColor = t.colors.accentTint.color,
                        unselectedIconColor = t.colors.ink.color, unselectedTextColor = t.colors.ink.color))
            } }
        }
    }
}
