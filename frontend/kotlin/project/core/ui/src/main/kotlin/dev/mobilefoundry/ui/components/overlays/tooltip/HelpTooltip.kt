package dev.mobilefoundry.ui.components.overlays.tooltip

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.overlays.popover.PopoverPanel

/** Tap-accessible short help, kept open until dismissed. No hover/long-press requirement or timer. */
@Composable
fun HelpTooltip(label: String, message: String, isPresented: Boolean, onShow: () -> Unit,
    onDismissRequest: () -> Unit, closeLabel: String, modifier: Modifier = Modifier) {
    PopoverPanel(label, isPresented, onDismissRequest, closeLabel, modifier,
        anchor = { ActionButton(onShow, variant = ButtonVariant.QUIET) { Text(label) } }) {
        Text(message)
    }
}
