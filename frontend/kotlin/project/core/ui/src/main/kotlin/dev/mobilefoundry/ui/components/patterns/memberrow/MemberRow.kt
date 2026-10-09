package dev.mobilefoundry.ui.components.patterns.memberrow

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive supplied identity with independent access and action slots. No role, membership or invitation policy. */
@Composable
fun MemberRow(name: String, accessibilityLabel: String, modifier: Modifier = Modifier, detail: String? = null,
    avatar: @Composable () -> Unit = {}, access: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    val identity: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.steps[1])) {
            Text(name, style = t.typography.label, color = t.colors.ink.color)
            if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.clearAndSetSemantics { contentDescription = accessibilityLabel }, verticalArrangement = Arrangement.spacedBy(t.space.inline)) { avatar(); identity() }
        } else Row(Modifier.clearAndSetSemantics { contentDescription = accessibilityLabel }, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
            avatar(); Column(Modifier.weight(1f)) { identity() }
        }
        access(); actions()
    }
}
