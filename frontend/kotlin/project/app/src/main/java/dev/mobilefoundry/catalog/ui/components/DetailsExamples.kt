package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.chip.ChoiceChip
import dev.mobilefoundry.ui.components.forms.stepper.ValueStepper
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.patterns.disclosure.DisclosureSection
import dev.mobilefoundry.ui.theme.FoundryTheme

internal data class DeliveryValues(val format: String = "Full size", val copies: Int = 0,
    val note: String = "Handle with care.", val expanded: Boolean = false, val enabled: Boolean = true, val applied: Int = 0)

@Composable
internal fun DetailsExamples(values: DeliveryValues, onChange: (DeliveryValues) -> Unit, onOpen: () -> Unit) {
    Card { DeliveryControls(values, onChange) }
    Card { DeliveryNote(values, onChange) }
    Card {
        KeyValueRow("Collection", "Atlas study")
        HorizontalDivider()
        KeyValueRow("Destination", "Personal collection on this device", detail = "Long detail values wrap without hiding their meaning.")
        HorizontalDivider()
        NavLink("Open delivery preview", onOpen, subtitle = "A detail screen with persistent actions")
        Text("Applied previews: ${values.applied}", style = FoundryTheme.tokens.typography.caption)
    }
}

@Composable
internal fun DeliveryControls(values: DeliveryValues, onChange: (DeliveryValues) -> Unit) {
    val t = FoundryTheme.tokens
    Text("Delivery choices", style = t.typography.heading, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(t.space.inline), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        listOf("Full size", "Compact size", "Print size").forEach { format ->
            ChoiceChip(format, values.format == format, { onChange(values.copy(format = format)) }, enabled = format != "Print size")
        }
    }
    Text("Export size: ${values.format}", style = t.typography.caption)
    ValueStepper("Copy count", values.copies, { onChange(values.copy(copies = it)) }, "${values.copies} copies",
        "Fewer copies", "More copies", range = 0..5, step = 2, enabled = values.enabled)
    Text("Change by two, up to five copies.", style = t.typography.caption)
    ToggleField("Enable quantity control", values.enabled, { onChange(values.copy(enabled = it)) })
}

@Composable
internal fun DeliveryNote(values: DeliveryValues, onChange: (DeliveryValues) -> Unit) {
    val focus = LocalFocusManager.current
    DisclosureSection("Delivery note", values.expanded, { focus.clearFocus(); onChange(values.copy(expanded = it)) },
        if (values.expanded) "Expanded" else "Collapsed", subtitle = "Optional instructions") {
        LabeledTextField("Package note", values.note, { onChange(values.copy(note = it)) }, Modifier.fillMaxWidth())
        ActionButton({ focus.clearFocus() }, variant = ButtonVariant.QUIET) { Text("Done editing note") }
    }
}
