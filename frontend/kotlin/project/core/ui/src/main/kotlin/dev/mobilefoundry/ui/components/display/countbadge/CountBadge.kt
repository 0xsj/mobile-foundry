package dev.mobilefoundry.ui.components.display.countbadge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive caller-formatted count/narration. Zero visibility, capping and pluralization belong to the host. */
@Composable
fun CountBadge(text: String, accessibilityLabel: String, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    Text(text, modifier.background(t.colors.accentTint.color, RoundedCornerShape(t.shape.pill))
        .padding(horizontal = t.space.inline, vertical = t.space.steps[1])
        .clearAndSetSemantics { contentDescription = accessibilityLabel },
        style = t.typography.caption, fontWeight = FontWeight.SemiBold, color = t.colors.accent.color)
}
