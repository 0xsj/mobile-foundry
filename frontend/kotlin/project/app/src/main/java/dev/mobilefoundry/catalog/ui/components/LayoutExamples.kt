package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.aspectratio.MediaFrame
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.components.layout.grid.AdaptiveGrid
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun LayoutExamples(narrow: Boolean, onNarrow: (Boolean) -> Unit, selected: String, onSelect: (String) -> Unit) {
    val t = FoundryTheme.tokens
    ToggleField("Narrow content preview", narrow, onNarrow)
    Text("Cards reflow to fit the available width and text size.", style = t.typography.caption)
    ContentContainer(maximumWidth = if (narrow) 240.dp else 720.dp, contentPadding = PaddingValues(0.dp)) {
        AdaptiveGrid(minimumItemWidth = 140.dp) {
            listOf("Atlas", "Orbit", "Field").forEach { name -> key(name) {
                Card {
                    MediaFrame(ratio = 4f / 3f, modifier = Modifier.clearAndSetSemantics { contentDescription = "$name preview" }) {
                        Canvas(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.accentTint.color, t.colors.surfaceSunk.color)))) {
                            drawCircle(t.colors.accent.color, radius = (size.minDimension / 2 - 20.dp.toPx()).coerceAtLeast(0f), style = Stroke(3.dp.toPx()))
                        }
                    }
                    Text(name, style = t.typography.label)
                    Text(if (name == "Orbit") "A longer description that grows with your preferred text size." else "A small study.", style = t.typography.caption)
                    ActionButton({ onSelect(name) }, variant = ButtonVariant.SECONDARY) { Text("Pick $name") }
                }
            } }
        }
    }
    Text("Picked layout item: $selected", style = t.typography.caption)
}
