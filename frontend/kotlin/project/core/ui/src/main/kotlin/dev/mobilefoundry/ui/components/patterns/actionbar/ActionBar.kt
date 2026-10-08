package dev.mobilefoundry.ui.components.patterns.actionbar

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Floating action region with optional supplied summary. Its host owns placement and operations. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, summary: String? = null, actions: @Composable ColumnScope.() -> Unit) {
    Card(modifier, role = SurfaceRole.FLOATING) {
        if (summary != null) Text(summary, style = FoundryTheme.tokens.typography.caption)
        actions()
    }
}
