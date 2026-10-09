package dev.mobilefoundry.ui.components.display.pricelabel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Supplied price copy and narration. Formatting, comparison and currency policy belong to the host. */
@Composable
fun PriceLabel(value: String, accessibilityLabel: String, modifier: Modifier = Modifier, comparison: String? = null, detail: String? = null) {
    val t = FoundryTheme.tokens
    Column(modifier.clearAndSetSemantics { contentDescription = accessibilityLabel }, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(value, style = t.typography.heading, color = t.colors.ink.color)
        if (comparison != null) Text(comparison, style = t.typography.body, color = t.colors.inkSecondary.color, textDecoration = TextDecoration.LineThrough)
        if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
    }
}
