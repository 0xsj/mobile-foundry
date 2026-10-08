package dev.mobilefoundry.ui.components.forms.toggle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One full-row switch target; the caller owns the checked value. */
@Composable
fun ToggleField(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, help: String? = null, enabled: Boolean = true) {
    val t = FoundryTheme.tokens
    Row(modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = t.typography.label)
            help?.let { Text(it, style = t.typography.caption, color = t.colors.inkSecondary.color) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
