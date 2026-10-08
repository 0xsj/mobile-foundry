package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.overlays.menu.ActionMenu
import dev.mobilefoundry.ui.components.overlays.menu.MenuAction
import dev.mobilefoundry.ui.components.patterns.pageheader.PageHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun OverlayExamples(copies: Int, lastAction: String, onShowSheet: () -> Unit,
    onResetRequest: () -> Unit, onDuplicate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            PageHeader("Native presentations", "Tap outside, dismiss, or confirm. Only explicit actions change the example.")
            ActionButton(onShowSheet, variant = ButtonVariant.SECONDARY) { Text("Show workspace sheet") }
            ActionButton(onResetRequest, variant = ButtonVariant.DESTRUCTIVE) { Text("Reset copies") }
            ActionMenu("Workspace actions", listOf(
                MenuAction("duplicate", "Duplicate workspace", onSelect = onDuplicate),
                MenuAction("share", "Share workspace", enabled = false, onSelect = {}),
                MenuAction("reset", "Reset copies…", destructive = true, onSelect = onResetRequest)))
        }
        Card {
            ListRow("Copies created", "$copies")
            Text("Last action: $lastAction", style = FoundryTheme.tokens.typography.caption)
        }
    }
}
