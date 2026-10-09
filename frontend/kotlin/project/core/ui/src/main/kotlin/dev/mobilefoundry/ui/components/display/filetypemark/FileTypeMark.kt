package dev.mobilefoundry.ui.components.display.filetypemark

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive supplied format/kind copy. No extension parsing, MIME inference or file access. */
@Composable
fun FileTypeMark(text: String, accessibilityLabel: String, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    val shape = RoundedCornerShape(t.shape.radii[1])
    Text(text, modifier.background(t.colors.accentTint.color, shape).border(1.dp, t.colors.accent.color, shape)
        .padding(t.space.inline).sizeIn(minWidth = 40.dp, minHeight = 40.dp)
        .clearAndSetSemantics { contentDescription = accessibilityLabel },
        style = t.typography.caption, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = t.colors.accent.color)
}
