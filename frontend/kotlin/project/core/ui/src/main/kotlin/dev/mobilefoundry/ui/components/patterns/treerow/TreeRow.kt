package dev.mobilefoundry.ui.components.patterns.treerow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Supplied branch state and an independent native disclosure. No traversal or expansion ownership. */
data class TreeDisclosure(val expanded: Boolean, val actionLabel: String, val stateLabel: String,
    val enabled: Boolean = true, val onToggle: () -> Unit)

/** Native open, optional separate disclosure, passive artwork and sibling actions. Enabled gates opening only. */
@Composable
fun TreeRow(title: String, accessibilityLabel: String, onOpen: () -> Unit, modifier: Modifier = Modifier,
    subtitle: String? = null, depth: Int = 0, indentation: Dp = 16.dp, maximumIndentation: Dp = 48.dp,
    selected: Boolean = false, enabled: Boolean = true, disclosure: TreeDisclosure? = null,
    leading: @Composable () -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    require(depth >= 0 && indentation.value.isFinite() && indentation.value >= 0 && maximumIndentation.value.isFinite() && maximumIndentation.value >= 0)
    val t = FoundryTheme.tokens
    val indent = minOf(depth.toDouble() * indentation.value, maximumIndentation.value.toDouble()).toFloat().dp
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Column(modifier.fillMaxWidth().padding(start = indent), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
            if (disclosure != null) IconAction(disclosure.actionLabel, { if (disclosure.enabled) disclosure.onToggle() },
                Modifier.semantics { stateDescription = disclosure.stateLabel }, enabled = disclosure.enabled) {
                Text(if (disclosure.expanded) "⌄" else if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "‹" else "›")
            } else Spacer(Modifier.width(t.shape.minimumInteractive))
            Column(Modifier.weight(1f).heightIn(min = t.shape.minimumInteractive)
                .background(if (selected) t.colors.accentTint.color else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(t.shape.radii[1]))
                .clickable(enabled = enabled, role = Role.Button) { if (enabled) onOpen() }
                .semantics { contentDescription = accessibilityLabel; this.selected = selected }.padding(t.space.steps[1])) {
                if (largeText) Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) { leading(); TreeCopy(title, subtitle, selected) }
                else Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
                    leading(); Column(Modifier.weight(1f)) { TreeCopy(title, subtitle, selected) }
                }
            }
        }
        actions()
    }
}
@Composable
private fun TreeCopy(title: String, subtitle: String?, selected: Boolean) {
    val t = FoundryTheme.tokens
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.steps[1])) {
        Text(title, style = t.typography.body, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (subtitle != null) Text(subtitle, style = t.typography.caption, color = t.colors.inkSecondary.color)
    }
}
