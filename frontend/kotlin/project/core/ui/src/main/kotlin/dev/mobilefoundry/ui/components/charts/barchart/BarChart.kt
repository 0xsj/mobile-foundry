package dev.mobilefoundry.ui.components.charts.barchart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

data class ChartBar(val id: String, val label: String, val value: Double, val valueLabel: String) {
    init { require(value.isFinite() && value >= 0) }
}
/** Small eager nonnegative bars with an explicit common positive maximum and caller-formatted values. */
@Composable
fun BarChart(title: String, bars: List<ChartBar>, maximum: Double, modifier: Modifier = Modifier, color: Color? = null) {
    require(maximum.isFinite() && maximum > 0 && bars.all { it.value <= maximum })
    require(bars.map { it.id }.distinct().size == bars.size)
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth().semantics { contentDescription = title }, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        bars.forEach { bar ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
                    Text(bar.label, style = t.typography.caption)
                    Text(bar.valueLabel, style = t.typography.caption)
                }
                Box(Modifier.fillMaxWidth().height(12.dp).background(t.colors.line.color, CircleShape).clearAndSetSemantics {}) {
                    Box(Modifier.fillMaxWidth((bar.value / maximum).toFloat()).fillMaxHeight().align(Alignment.CenterStart)
                        .background(color ?: t.colors.accent.color, CircleShape))
                }
            }
        }
    }
}
