package dev.mobilefoundry.ui.components.shells.appshell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.layout.surface.Backdrop

/** Bounded backdrop/content/navigation slots. Host owns routes, scrolling, insets, keyboard and feature lifetime. */
@Composable
fun AppShell(modifier: Modifier = Modifier, background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit, navigation: @Composable () -> Unit = {}) {
    BoxWithConstraints(modifier) {
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) { "AppShell requires a bounded viewport" }
        Backdrop(Modifier.fillMaxSize(), background = {
            Box(Modifier.matchParentSize().clearAndSetSemantics { }, content = background)
        }) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), content = content)
                navigation()
            }
        }
    }
}
