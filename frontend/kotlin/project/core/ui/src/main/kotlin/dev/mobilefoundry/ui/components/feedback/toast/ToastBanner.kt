package dev.mobilefoundry.ui.components.feedback.toast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

data class ToastAction(val title: String, val onPerform: () -> Unit)

/** Host-owned presence, timeout and queue. Dismissal and action are separate explicit callbacks. */
@Composable
fun ToastBanner(message: String, dismissLabel: String, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, tone: MessageTone = MessageTone.INFO, action: ToastAction? = null) {
    val t = FoundryTheme.tokens
    Card(modifier, role = SurfaceRole.FLOATING) {
        Text(message, color = tone.color(t), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        // Vertical actions accommodate long translations and large fonts without clipping.
        action?.let { ActionButton(it.onPerform, variant = ButtonVariant.SECONDARY) { Text(it.title) } }
        ActionButton(onDismiss, variant = ButtonVariant.QUIET) { Text(dismissLabel) }
    }
}
