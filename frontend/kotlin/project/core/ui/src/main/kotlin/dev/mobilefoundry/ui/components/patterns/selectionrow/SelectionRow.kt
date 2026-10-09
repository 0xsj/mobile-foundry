package dev.mobilefoundry.ui.components.patterns.selectionrow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One whole-row choice, distinct from opening an item. Leading artwork must be passive. */
@Composable
fun SelectionRow(title: String, selected: Boolean, stateDescription: String, onToggle: () -> Unit,
    modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true, leading: @Composable () -> Unit = {}) {
    ListRow(title, subtitle, modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox,
        onValueChange = { onToggle() }).semantics { this.stateDescription = stateDescription }, leading = {
        Row(horizontalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.inline), modifier = Modifier.clearAndSetSemantics {}) {
            Checkbox(selected, onCheckedChange = null, enabled = enabled)
            leading()
        }
    })
}
