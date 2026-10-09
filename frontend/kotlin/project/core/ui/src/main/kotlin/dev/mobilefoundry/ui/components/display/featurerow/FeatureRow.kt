package dev.mobilefoundry.ui.components.display.featurerow

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive feature/availability copy. Complete narration supplies the meaning of the decorative mark. */
@Composable
fun FeatureRow(title: String, stateLabel: String, included: Boolean, accessibilityLabel: String,
    modifier: Modifier = Modifier, detail: String? = null) {
    val t = FoundryTheme.tokens
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = accessibilityLabel },
        horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(if (included) "✓" else "−", color = if (included) t.colors.accent.color else t.colors.inkSecondary.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.space.steps[1])) {
            Text(title, style = t.typography.body)
            Text(stateLabel, style = t.typography.caption, color = t.colors.inkSecondary.color)
            if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
        }
    }
}
