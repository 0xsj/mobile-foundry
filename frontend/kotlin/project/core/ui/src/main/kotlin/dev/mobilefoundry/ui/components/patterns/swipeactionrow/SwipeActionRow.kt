package dev.mobilefoundry.ui.components.patterns.swipeactionrow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

data class SwipeAction(val title: String, val onPerform: () -> Unit, val destructive: Boolean = false, val enabled: Boolean = true)

/** Native half-width swipe completion, one action per logical edge. No domain mutation/undo here.
 * Gesture state is ephemeral and reset before dispatch; callers key row composition by stable identity. */
@Composable
fun SwipeActionRow(label: String, modifier: Modifier = Modifier, leading: SwipeAction? = null,
    trailing: SwipeAction? = null, enabled: Boolean = true, content: @Composable () -> Unit) {
    val t = FoundryTheme.tokens
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * .5f }) }
    val currentLeading by rememberUpdatedState(leading?.takeIf { enabled && it.enabled })
    val currentTrailing by rememberUpdatedState(trailing?.takeIf { enabled && it.enabled })
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { direction ->
            if (direction != SwipeToDismissBoxValue.Settled) {
                val action = if (direction == SwipeToDismissBoxValue.StartToEnd) currentLeading else currentTrailing
                state.snapTo(SwipeToDismissBoxValue.Settled)
                action?.onPerform?.invoke()
            }
        }
    }
    Box(modifier.semantics {
        contentDescription = label
        customActions = listOfNotNull(currentLeading, currentTrailing).map { action ->
            CustomAccessibilityAction(action.title) { action.onPerform(); true }
        }
    }) {
        SwipeToDismissBox(state, enableDismissFromStartToEnd = currentLeading != null,
            enableDismissFromEndToStart = currentTrailing != null, gesturesEnabled = enabled,
            backgroundContent = {
                val action = when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> currentLeading
                    SwipeToDismissBoxValue.EndToStart -> currentTrailing
                    SwipeToDismissBoxValue.Settled -> null
                }
                Box(Modifier.fillMaxSize().background(if (action?.destructive == true) t.colors.crit.color else t.colors.accentTint.color)
                    .padding(t.space.stack).clearAndSetSemantics {},
                    contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                    if (action != null) Text(action.title, color = if (action.destructive) t.colors.surfacePanel.color else t.colors.ink.color)
                }
            }) { content() }
    }
}
