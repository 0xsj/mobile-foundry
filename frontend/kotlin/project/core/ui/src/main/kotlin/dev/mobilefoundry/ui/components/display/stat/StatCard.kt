package dev.mobilefoundry.ui.components.display.stat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Receives formatted copy; trend meaning and numeric calculations belong to the caller. */
@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, detail: String? = null,
    trend: String? = null, tone: MessageTone = MessageTone.NEUTRAL, role: SurfaceRole = SurfaceRole.CONTENT) {
    val t = FoundryTheme.tokens
    Card(modifier.semantics(mergeDescendants = true) {}, role) {
        Text(title, style = t.typography.label)
        Text(value, style = t.typography.title)
        trend?.let { Text(it, style = t.typography.caption, color = tone.color(t)) }
        detail?.let { Text(it, style = t.typography.caption, color = t.colors.inkSecondary.color) }
    }
}
