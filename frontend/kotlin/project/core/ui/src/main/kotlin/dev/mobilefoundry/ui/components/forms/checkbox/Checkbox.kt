package dev.mobilefoundry.ui.components.forms.checkbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class CheckState { OFF, ON, MIXED }

/** The caller decides how a mixed selection advances on activation. */
@Composable
fun Checkbox(title: String, state: CheckState, stateDescription: String, onToggle: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = FoundryTheme.tokens
    val native = when (state) { CheckState.OFF -> ToggleableState.Off; CheckState.ON -> ToggleableState.On; CheckState.MIXED -> ToggleableState.Indeterminate }
    Row(modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive)
        .triStateToggleable(native, enabled = enabled, role = Role.Checkbox, onClick = onToggle)
        .semantics { this.stateDescription = stateDescription },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
        TriStateCheckbox(native, onClick = null, enabled = enabled)
        Text(title, style = t.typography.body, modifier = Modifier.weight(1f))
    }
}
