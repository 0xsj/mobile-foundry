package dev.mobilefoundry.ui.components.patterns.messagecomposer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.multiline.MultilineField
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Native multiline draft. The caller owns send eligibility, focus, attachments and post-send clearing.
 * Slot controls receive interactivity so callers can gate their independent actions. */
@Composable
fun MessageComposer(title: String, state: TextFieldState, sendLabel: String, canSend: Boolean, onSend: () -> Unit,
    modifier: Modifier = Modifier, isSending: Boolean = false, enabled: Boolean = true, help: String? = null,
    attachments: @Composable ColumnScope.(interactive: Boolean) -> Unit = {},
    actions: @Composable FlowRowScope.(interactive: Boolean) -> Unit = {}) {
    val t = FoundryTheme.tokens
    val interactive = enabled && !isSending
    Surface(modifier, role = SurfaceRole.FLOATING) {
        Column(Modifier.fillMaxWidth().padding(t.space.stack), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            attachments(interactive)
            MultilineField(title, state, Modifier.fillMaxWidth(), help = help, enabled = interactive, lines = 1..5)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(t.space.inline), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                actions(interactive)
                ActionButton(onSend, enabled = enabled && canSend, isBusy = isSending) { Text(sendLabel) }
            }
        }
    }
}
