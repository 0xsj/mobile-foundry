package dev.mobilefoundry.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import dev.mobilefoundry.product.screens.*
import dev.mobilefoundry.ui.components.navigation.tabbar.TabBar
import dev.mobilefoundry.ui.components.navigation.tabbar.TabItem
import dev.mobilefoundry.ui.components.shells.appshell.AppShell
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun ProductRoot(configuration: ProductConfiguration) {
    var selected by rememberSaveable { mutableStateOf(ProductTab.TODAY.id) }
    var glass by rememberSaveable { mutableStateOf(configuration.defaultGlass) }
    FoundryTheme(style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
        val t = FoundryTheme.tokens
        AppShell(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), background = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
                t.colors.surfaceGround.color, t.colors.accentTint.color, t.colors.surfaceRaised.color))))
        }, content = {
            when (ProductTab.entries.first { it.id == selected }) {
                ProductTab.TODAY -> TodayScreen(configuration.displayName)
                ProductTab.LIBRARY -> LibraryScreen()
                ProductTab.ACTIVITY -> ActivityScreen()
                ProductTab.ACCOUNT -> AccountScreen(glass, onGlassChange = { glass = it })
            }
        }, navigation = {
            TabBar(ProductTab.entries.map { tab ->
                TabItem(tab.id, tab.title) { Icon(painterResource(tab.icon), contentDescription = null) }
            }, selectedId = selected, onSelect = { id ->
                if (ProductTab.entries.any { it.id == id }) selected = id
            }, modifier = Modifier.padding(horizontal = t.space.page, vertical = t.space.inline))
        })
    }
}
