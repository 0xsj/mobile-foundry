package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.components.patterns.actionbar.ActionBar
import dev.mobilefoundry.ui.components.patterns.pageheader.PageHeader
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun DeliveryPreviewScreen(values: DeliveryValues, onChange: (DeliveryValues) -> Unit, onBack: () -> Unit) {
    val t = FoundryTheme.tokens
    BackHandler(onBack = onBack)
    DetailShell(header = {
        Column {
            TextButton(onClick = onBack) { Text("Back to components") }
            ContentContainer { PageHeader("Atlas delivery", "Review a local preview.") }
        }
    }, content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
                    Card {
                        KeyValueRow("Collection", "Atlas study")
                        HorizontalDivider()
                        KeyValueRow("Destination", "Personal collection on this device")
                        HorizontalDivider()
                        KeyValueRow("Availability", "Local preview only", detail = "These controls change this preview.")
                    }
                    Card { DeliveryControls(values, onChange) }
                    Card { DeliveryNote(values, onChange) }
                }
            }
        }
    }, actions = {
        ContentContainer {
            ActionBar(summary = "${values.copies} copies · ${values.format}") {
                ActionButton({ onChange(values.copy(applied = values.applied + 1)) }, Modifier.fillMaxWidth(), enabled = values.copies > 0) { Text("Apply preview") }
                ActionButton({ onChange(DeliveryValues(applied = values.applied)) }, variant = ButtonVariant.QUIET) { Text("Reset delivery") }
                Text("Applied previews: ${values.applied}", style = t.typography.caption)
            }
        }
    })
}
