package dev.mobilefoundry.ui.components.overlays.popover

import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Native focusable anchored menu surface with outside/back dismissal. Presentation belongs to the caller.
 * DropdownMenu supplies vertical scrolling; do not nest a vertically scrolling collection in the slot. */
@Composable
fun PopoverPanel(title: String, isPresented: Boolean, onDismissRequest: () -> Unit, closeLabel: String,
    modifier: Modifier = Modifier, anchor: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Box(modifier) {
        anchor()
        DropdownMenu(expanded = isPresented, onDismissRequest = onDismissRequest,
            modifier = Modifier.widthIn(min = 200.dp, max = 320.dp), containerColor = t.colors.surfaceRaised.color) {
            Column(Modifier.padding(t.space.page), verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
                Text(title, style = t.typography.heading, modifier = Modifier.semantics { heading() })
                content()
                ActionButton(onDismissRequest, variant = ButtonVariant.QUIET) { Text(closeLabel) }
            }
        }
    }
}
