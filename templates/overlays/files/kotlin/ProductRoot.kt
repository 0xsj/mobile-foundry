package dev.mobilefoundry.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobilefoundry.product.connection.ConnectedViewModel
import dev.mobilefoundry.product.screens.*
import dev.mobilefoundry.ui.components.navigation.tabbar.TabBar
import dev.mobilefoundry.ui.components.navigation.tabbar.TabItem
import dev.mobilefoundry.ui.components.shells.appshell.AppShell
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun ProductRoot(configuration: ProductConfiguration) {
    val owner: ConnectedViewModel = viewModel()
    val model = owner.connection
    var selected by rememberSaveable { mutableStateOf(ProductTab.TODAY.id) }
    var glass by rememberSaveable { mutableStateOf(configuration.defaultGlass) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> model?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        lifecycle.addObserver(observer)
        model?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); model?.setForeground(false) }
    }
    FoundryTheme(style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
        val t = FoundryTheme.tokens
        if (model == null) { Text(owner.configurationFailure ?: "Opening your workspace…"); return@FoundryTheme }
        AppShell(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), background = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.surfaceGround.color,
                t.colors.accentTint.color, t.colors.surfaceRaised.color))))
        }, content = {
            when (ProductTab.entries.first { it.id == selected }) {
                ProductTab.TODAY -> TodayScreen(model)
                ProductTab.LIBRARY -> LibraryScreen(model)
                ProductTab.ACTIVITY -> ActivityScreen()
                ProductTab.ACCOUNT -> AccountScreen(glass, { glass = it }, model)
            }
        }, navigation = {
            TabBar(ProductTab.entries.map { tab -> TabItem(tab.id, tab.title) { Icon(painterResource(tab.icon), null) } },
                selectedId = selected, onSelect = { id -> if (ProductTab.entries.any { it.id == id }) selected = id },
                modifier = Modifier.padding(horizontal = t.space.page, vertical = t.space.inline))
        })
    }
}
