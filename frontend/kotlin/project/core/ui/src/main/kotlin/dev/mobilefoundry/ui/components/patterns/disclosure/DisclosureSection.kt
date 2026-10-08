package dev.mobilefoundry.ui.components.patterns.disclosure

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** A full-header native action with separately interactive revealed content.
 * Hoist drafts above this view: collapsed content has no promised state lifetime. */
@Composable
fun DisclosureSection(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    stateDescription: String, modifier: Modifier = Modifier, subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        ListRow(title, subtitle, Modifier.clickable(role = Role.Button) { onExpandedChange(!expanded) }
            .semantics { this.stateDescription = stateDescription },
            trailing = { Text(if (expanded) "⌃" else "⌄", Modifier.clearAndSetSemantics {}) })
        if (t.motion.reduced) {
            if (expanded) Column(verticalArrangement = Arrangement.spacedBy(t.space.stack), content = content)
        } else {
            val duration = t.motion.milliseconds[1]
            AnimatedVisibility(expanded,
                enter = expandVertically(tween(duration, easing = t.motion.standardEasing)) + fadeIn(tween(duration)),
                exit = shrinkVertically(tween(duration, easing = t.motion.standardEasing)) + fadeOut(tween(duration))) {
                Column(verticalArrangement = Arrangement.spacedBy(t.space.stack), content = content)
            }
        }
    }
}
