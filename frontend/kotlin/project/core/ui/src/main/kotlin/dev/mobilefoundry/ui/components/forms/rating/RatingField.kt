package dev.mobilefoundry.ui.components.forms.rating

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Controlled native choices from 1..maximum (at most 10). Zero is unrated; clearing is caller-owned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingField(title: String, value: Int, onValueChange: (Int) -> Unit, valueLabel: String,
    optionLabel: (Int) -> String, modifier: Modifier = Modifier, maximum: Int = 5, enabled: Boolean = true) {
    require(maximum in 1..10 && value in 0..maximum)
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label)
        Text(valueLabel, style = t.typography.caption, color = t.colors.inkSecondary.color)
        FlowRow {
            (1..maximum).forEach { score ->
                TextButton(onClick = { onValueChange(score) }, enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = t.shape.minimumInteractive, minHeight = t.shape.minimumInteractive)
                        .semantics { contentDescription = optionLabel(score); selected = score == value }) {
                    Text(if (score <= value) "★" else "☆", modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}
