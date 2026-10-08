package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.avatar.Avatar
import dev.mobilefoundry.ui.components.display.avatar.AvatarShape
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.display.stat.StatCard
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun DisplayExamples(artwork: Boolean, onArtwork: (Boolean) -> Unit) {
    val t = FoundryTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card {
            Text("People and workspaces", style = t.typography.heading)
            ListRow("Jordan Lee", "Initials supplied by the caller", leading = { Avatar("Jordan profile", "JL") })
            ListRow("Design studio", if (artwork) "Custom artwork slot" else "Fallback while artwork is unavailable", leading = {
                Avatar("Studio emblem", "ST", size = 64.dp, shape = AvatarShape.ROUNDED,
                    content = if (artwork) {{
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.accent.color, t.colors.info.color))),
                            contentAlignment = Alignment.Center) { Text("✦", style = t.typography.title, color = t.colors.surfaceGround.color) }
                    }} else null)
            })
            ToggleField("Show custom artwork", artwork, onArtwork)
        }
        StatCard("Active projects", "12", detail = "Illustrative workspace data", trend = "3 added this week", tone = MessageTone.INFO)
        StatCard("Storage used", "2.4 GB", detail = "Of your 5 GB local budget", trend = "Review before adding large files",
            tone = MessageTone.WARNING, role = SurfaceRole.FLOATING)
    }
}
