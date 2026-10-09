package dev.mobilefoundry.ui.components.navigation.daystrip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.theme.FoundryTheme

data class DayOption(val id: String, val label: String, val valueLabel: String, val accessibilityLabel: String,
    val detail: String? = null, val enabled: Boolean = true)

/** A small caller-supplied set of days. Wraps at narrow widths; does not generate a calendar or choose a default. */
@Composable
fun DayStrip(title: String, options: List<DayOption>, selection: String?, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    require(options.map { it.id }.distinct().size == options.size)
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label, modifier = Modifier.semantics { heading() })
        WrapLayout(Modifier.selectableGroup()) {
            options.forEach { day -> key(day.id) {
                val selected = selection == day.id
                val shape = RoundedCornerShape(t.shape.radii[2])
                Column(Modifier.clip(shape).background(if (selected) t.colors.accentTint.color else t.colors.surfacePanel.color)
                    .border(1.dp, if (selected) t.colors.accent.color else t.colors.line.color, shape)
                    .selectable(selected, enabled = enabled && day.enabled, role = Role.RadioButton, onClick = { onSelect(day.id) })
                    .semantics(mergeDescendants = true) { contentDescription = day.accessibilityLabel }
                    .alpha(if (enabled && day.enabled) 1f else .5f).padding(t.space.inline)
                    .sizeIn(minWidth = t.shape.minimumInteractive, minHeight = t.shape.minimumInteractive),
                    verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                    Text(day.label, style = t.typography.caption)
                    Text(day.valueLabel, style = t.typography.heading)
                    day.detail?.let { Text(it, style = t.typography.caption) }
                }
            } }
        }
    }
}
