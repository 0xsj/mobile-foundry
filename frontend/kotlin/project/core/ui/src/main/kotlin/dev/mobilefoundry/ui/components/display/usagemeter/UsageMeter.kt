package dev.mobilefoundry.ui.components.display.usagemeter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive supplied usage. A finite fraction clamps to 0..1; null/nonfinite hides the decorative bar, not loading. */
@Composable
fun UsageMeter(title: String, value: String, accessibilityLabel: String, modifier: Modifier = Modifier,
    detail: String? = null, fraction: Float? = null) {
    val t = FoundryTheme.tokens
    val normalized = fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = accessibilityLabel },
        verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label)
        Text(value, style = t.typography.heading)
        if (normalized != null) LinearProgressIndicator(progress = { normalized }, modifier = Modifier.fillMaxWidth(), color = t.colors.accent.color)
        if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
    }
}
