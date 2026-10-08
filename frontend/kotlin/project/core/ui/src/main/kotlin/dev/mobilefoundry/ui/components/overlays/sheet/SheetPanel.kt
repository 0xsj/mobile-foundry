package dev.mobilefoundry.ui.components.overlays.sheet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Native presentation with a caller-controlled lifetime and content slot. Supply scrolling in the slot when needed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetPanel(title: String, isPresented: Boolean, onDismissRequest: () -> Unit, closeLabel: String,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (isPresented) ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        val t = FoundryTheme.tokens
        Column(Modifier.fillMaxWidth().padding(t.space.page), verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            Text(title, style = t.typography.heading, modifier = Modifier.semantics { heading() })
            content()
            ActionButton(onDismissRequest, variant = ButtonVariant.SECONDARY) { Text(closeLabel) }
        }
    }
}
