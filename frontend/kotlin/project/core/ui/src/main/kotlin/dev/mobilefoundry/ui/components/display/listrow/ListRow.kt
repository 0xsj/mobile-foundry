package dev.mobilefoundry.ui.components.display.listrow

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Layout only. Supply a clickable modifier or native parent for a row action. */
@Composable
fun ListRow(title: String, subtitle: String? = null, modifier: Modifier = Modifier,
            leading: @Composable () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    val t = FoundryTheme.tokens
    Row(modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.space.steps[1])) {
            Text(title, style = t.typography.label)
            if (subtitle != null) Text(subtitle, style = t.typography.caption, color = t.colors.inkSecondary.color)
            if (LocalDensity.current.fontScale >= 1.5f) trailing()
        }
        if (LocalDensity.current.fontScale < 1.5f) trailing()
    }
}
