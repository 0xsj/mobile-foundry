package dev.mobilefoundry.ui.components.charts.progressring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Determinate ring, finite fraction clamps to 0..1. The caller supplies value copy/meaning. */
@Composable
fun ProgressRing(label: String, fraction: Double, valueLabel: String, modifier: Modifier = Modifier,
    diameter: Dp = 120.dp, thickness: Dp = 8.dp, color: Color? = null) {
    require(fraction.isFinite() && diameter.value.isFinite() && thickness.value.isFinite() && diameter > 0.dp && thickness > 0.dp && thickness < diameter / 2)
    val progress = fraction.coerceIn(0.0, 1.0).toFloat()
    val t = FoundryTheme.tokens
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Column(modifier.progressSemantics(progress).semantics { contentDescription = label; stateDescription = valueLabel },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Box(Modifier.size(diameter).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = thickness.toPx(); val inset = stroke / 2
                val bounds = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f))
                drawArc(t.colors.line.color, -90f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
                if (progress > 0) drawArc(color ?: t.colors.accent.color, -90f, progress * 360, false, Offset(inset, inset), bounds,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (!largeText) Text(valueLabel, Modifier.padding(thickness * 2), style = t.typography.label, textAlign = TextAlign.Center)
        }
        if (largeText) Text(valueLabel, Modifier.clearAndSetSemantics {}, style = t.typography.label)
        Text(label, Modifier.clearAndSetSemantics {}, style = t.typography.caption, textAlign = TextAlign.Center)
    }
}
