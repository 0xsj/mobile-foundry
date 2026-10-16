package dev.mobilefoundry.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.product.screens.ProductContent
import androidx.compose.ui.graphics.Brush
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.shells.appshell.AppShell
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun ProductRoot(configuration: ProductConfiguration) {
    // Save navigation above theme and layout branches, rather than inside a pane.
    var state by rememberSaveable(stateSaver = ProductState.Saver) {
        mutableStateOf(ProductState(glass = configuration.defaultGlass))
    }
    FoundryTheme(style = if (state.glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
        val t = FoundryTheme.tokens
        AppShell(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), background = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
                t.colors.surfaceGround.color, t.colors.accentTint.color, t.colors.surfaceRaised.color))))
        }, content = {
            DetailShell(header = {
                Column(Modifier.padding(horizontal = t.space.page, vertical = t.space.inline),
                    verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                    SectionHeader(configuration.displayName, ProductState.shellTitle)
                    ToggleField("Glass", state.glass, onCheckedChange = { state = state.copy(glass = it) })
                }
            }, content = { ProductContent(state, onStateChange = { state = it }) }, actions = {})
        })
    }
}
