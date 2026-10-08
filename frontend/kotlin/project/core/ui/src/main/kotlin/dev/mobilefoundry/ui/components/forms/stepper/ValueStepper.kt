package dev.mobilefoundry.ui.components.forms.stepper

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Bounded integer changes, clamped at endpoints. Copy and committed value belong to the caller. */
@Composable
fun ValueStepper(title: String, value: Int, onValueChange: (Int) -> Unit, valueLabel: String,
    decreaseLabel: String, increaseLabel: String, modifier: Modifier = Modifier, range: IntRange = 0..10,
    step: Int = 1, enabled: Boolean = true) {
    require(!range.isEmpty() && value in range && step > 0)
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
            ActionButton({ onValueChange((value.toLong() - step).coerceAtLeast(range.first.toLong()).toInt()) },
                Modifier.semantics { contentDescription = decreaseLabel }, variant = ButtonVariant.SECONDARY,
                enabled = enabled && value > range.first) { Text("−", Modifier.clearAndSetSemantics {}) }
            Text(valueLabel, Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically), textAlign = TextAlign.Center)
            ActionButton({ onValueChange((value.toLong() + step).coerceAtMost(range.last.toLong()).toInt()) },
                Modifier.semantics { contentDescription = increaseLabel }, variant = ButtonVariant.SECONDARY,
                enabled = enabled && value < range.last) { Text("+", Modifier.clearAndSetSemantics {}) }
        }
    }
}
