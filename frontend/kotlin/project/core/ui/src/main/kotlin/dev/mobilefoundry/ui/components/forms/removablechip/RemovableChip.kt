package dev.mobilefoundry.ui.components.forms.removablechip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One remove action with supplied accessible copy. No selection, timeout or removal mutation. */
@Composable
fun RemovableChip(title: String, removeLabel: String, onRemove: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onRemove, modifier.heightIn(min = FoundryTheme.tokens.shape.minimumInteractive)
        .semantics { contentDescription = removeLabel }, enabled = enabled) {
        Text(title, modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {})
        Spacer(Modifier.width(FoundryTheme.tokens.space.inline))
        Text("×", Modifier.clearAndSetSemantics {})
    }
}
