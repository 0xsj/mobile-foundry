package dev.mobilefoundry.ui.components.forms.slider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.mobilefoundry.ui.theme.FoundryTheme

/** steps counts intermediate stops; zero is continuous. valueLabel is caller-formatted copy. */
@Composable
fun ValueSlider(title: String, value: Float, onValueChange: (Float) -> Unit, valueLabel: String,
    modifier: Modifier = Modifier, range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null) {
    require(range.start.isFinite() && range.endInclusive.isFinite() && range.start < range.endInclusive)
    require(value.isFinite() && value in range && steps >= 0)
    Column(modifier) {
        Text(title, style = FoundryTheme.tokens.typography.label)
        Text(valueLabel, style = FoundryTheme.tokens.typography.caption)
        Slider(value, onValueChange, enabled = enabled, valueRange = range, steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.semantics { contentDescription = title; stateDescription = valueLabel })
    }
}
