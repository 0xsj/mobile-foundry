package dev.mobilefoundry.ui.components.shells.detailshell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A bounded screen with fixed header/actions and a flexible body slot.
 * The caller supplies body scrolling, system insets, navigation and keyboard policy. */
@Composable
fun DetailShell(modifier: Modifier = Modifier, header: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit, actions: @Composable () -> Unit) {
    Column(modifier.fillMaxSize()) {
        header()
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        actions()
    }
}
