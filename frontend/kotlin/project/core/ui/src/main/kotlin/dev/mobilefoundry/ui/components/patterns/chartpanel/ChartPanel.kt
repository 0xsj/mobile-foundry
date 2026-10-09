package dev.mobilefoundry.ui.components.patterns.chartpanel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Plot bounds, data, independent controls and accessible summaries belong to the host. */
@Composable
fun ChartPanel(title: String, modifier: Modifier = Modifier, subtitle: String? = null, role: SurfaceRole = SurfaceRole.CONTENT,
    plot: @Composable () -> Unit, legend: @Composable FlowRowScope.() -> Unit = {}, footer: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Card(modifier, role = role) {
        Text(title, Modifier.semantics { heading() }, style = t.typography.heading)
        if (subtitle != null) Text(subtitle, style = t.typography.caption, color = t.colors.inkSecondary.color)
        plot()
        WrapLayout(content = legend)
        footer()
    }
}
