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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.popover.PopoverPanel
import dev.mobilefoundry.ui.components.overlays.tooltip.HelpTooltip
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun ContextExamples(help: Boolean, onHelp: (Boolean) -> Unit, options: Boolean, onOptions: (Boolean) -> Unit,
    choices: Int, onChoose: () -> Unit, onNavigate: () -> Unit) {
    val t = FoundryTheme.tokens
    Card {
        Text("Context and navigation", style = t.typography.heading, modifier = Modifier.semantics { heading() })
        HelpTooltip("About previews", "Previews stay on this device until you choose to share them.",
            help, { onHelp(true) }, { onHelp(false) }, "Close preview help")
        PopoverPanel("Storage options", options, { onOptions(false) }, "Close storage options",
            anchor = { ActionButton({ onOptions(true) }, variant = ButtonVariant.SECONDARY) { Text("Show storage options") } }) {
            Text("Keep a local copy for quick access.")
            ActionButton({ onChoose(); onOptions(false) }) { Text("Choose local storage") }
        }
        Text("Storage choices: $choices", style = t.typography.caption)
        HorizontalDivider()
        NavLink("Storage details", onNavigate, subtitle = "Open a separate example screen")
    }
}

@Composable
internal fun ComponentDetailScreen(onBack: () -> Unit) {
    val t = FoundryTheme.tokens
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back to components") }
        Text("Storage details", style = t.typography.title)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ContentContainer {
                Card {
                    Text("Local storage", style = t.typography.heading, modifier = Modifier.semantics { heading() })
                    Text("This is a placeholder destination. Your app supplies the route and its content.")
                }
            }
        }
    }
}
