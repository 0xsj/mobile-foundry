package dev.mobilefoundry.catalog.ui.shell.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.shells.appshell.AppShell
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.serialization.json.Json

val productShellStateSaver = Saver<ProductShellState, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<ProductShellState>(it) })
class ProductShellDrafts(val browse: TextFieldState, val canvas: TextFieldState, val name: TextFieldState)

@Composable fun ProductShellCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var values by rememberSaveable(stateSaver = productShellStateSaver) { mutableStateOf(ProductShellState()) }
    var preview by rememberSaveable { mutableStateOf<ProductShellPattern?>(null) }
    var glass by rememberSaveable { mutableStateOf(true) }
    val drafts = ProductShellDrafts(rememberTextFieldState(), rememberTextFieldState("Untitled canvas"), rememberTextFieldState())
    val action: (ProductShellAction) -> Unit = { event ->
        // Read the latest owner value for every event, including rapid repeated actions.
        values = values.apply(event)
        if (event == ProductShellAction.ResetSetup) drafts.name.edit { replace(0, length, "") }
    }
    val selected = preview
    if (selected != null) {
        BackHandler { preview = null }
        FoundryTheme(style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            ProductShellViewport(selected, values, action, drafts, glass, { glass = it }, { preview = null }, modifier)
        }
    } else {
        val t = FoundryTheme.tokens
        Column(modifier.fillMaxSize()) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Product shells", style = t.typography.title)
            VerticalStack(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
                SectionHeader("Start with a shape", "Five working layouts for common products. Each preview keeps its own local state.")
                ProductShellPattern.entries.forEach { pattern -> Card(role = SurfaceRole.FLOATING) {
                    NavLink("Open ${pattern.label}", { preview = pattern }, subtitle = pattern.subtitle)
                } }
            }
        }
    }
}
@Composable fun ProductShellViewport(pattern: ProductShellPattern, values: ProductShellState,
    onAction: (ProductShellAction) -> Unit, drafts: ProductShellDrafts, glass: Boolean, onGlass: (Boolean) -> Unit,
    onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = t.space.page), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text("Back to shells") }
            ToggleField("Glass", glass, onGlass, Modifier.weight(1f))
        }
        AppShell(Modifier.weight(1f).fillMaxWidth(), background = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.surfaceGround.color, t.colors.accentTint.color))))
        }, content = { ProductShellLayout(pattern, values, onAction, drafts) })
    }
}
