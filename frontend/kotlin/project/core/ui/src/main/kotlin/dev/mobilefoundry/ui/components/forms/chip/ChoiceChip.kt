package dev.mobilefoundry.ui.components.forms.chip

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.theme.FoundryTheme

/** A compact selected/unselected action. The caller coordinates single or multiple choices. */
@Composable
fun ChoiceChip(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    val t = FoundryTheme.tokens
    FilterChip(selected, onSelect, label = { Text(title) }, modifier = modifier.heightIn(min = t.shape.minimumInteractive),
        enabled = enabled, leadingIcon = leading, colors = FilterChipDefaults.filterChipColors(
            containerColor = t.colors.surfacePanel.color, labelColor = t.colors.ink.color,
            selectedContainerColor = t.colors.accentTint.color, selectedLabelColor = t.colors.accent.color))
}
