package dev.mobilefoundry.ui.components.patterns.profileheader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Supplied identity copy, decorative avatar and independent status/actions. No account or image ownership. */
@Composable
fun ProfileHeader(title: String, modifier: Modifier = Modifier, detail: String? = null,
    avatar: @Composable () -> Unit = {}, status: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    val copy: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(title, Modifier.semantics { heading() }, style = t.typography.heading)
            detail?.let { Text(it, style = t.typography.body, color = t.colors.inkSecondary.color) }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            Box(Modifier.clearAndSetSemantics {}) { avatar() }; copy()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
                Box(Modifier.clearAndSetSemantics {}) { avatar() }
                Box(Modifier.weight(1f)) { copy() }
            }
        }
        status(); actions()
    }
}
