package dev.mobilefoundry.ui.components.patterns.settingssection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, footer: String? = null,
                    role: SurfaceRole = SurfaceRole.CONTENT, content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Text(title, style = t.typography.label, modifier = Modifier.semantics { heading() })
        Card(role = role, content = content)
        if (footer != null) Text(footer, style = t.typography.caption, color = t.colors.inkSecondary.color)
    }
}
