package dev.mobilefoundry.ui.components.forms.iconaction

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant

/** Native named action with passive icon content. The host supplies localized meaning and effects. */
@Composable
fun IconAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.QUIET, enabled: Boolean = true, icon: @Composable () -> Unit) {
    ActionButton(onClick, modifier.semantics { contentDescription = label }, variant = variant, enabled = enabled) {
        Box(Modifier.clearAndSetSemantics {}) { icon() }
    }
}
