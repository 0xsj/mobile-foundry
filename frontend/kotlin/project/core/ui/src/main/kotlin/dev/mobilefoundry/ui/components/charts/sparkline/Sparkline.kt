package dev.mobilefoundry.ui.components.charts.sparkline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlin.math.abs

// Scale before subtracting so a finite range spanning -Double.MAX_VALUE...Double.MAX_VALUE cannot overflow.
internal fun normalizedSparkline(values: List<Double>): List<Pair<Double, Double>> {
    require(values.all { it.isFinite() })
    if (values.isEmpty()) return emptyList()
    val scale = values.maxOf { abs(it) }
    val scaled = if (scale == 0.0) values else values.map { it / scale }
    val low = scaled.min(); val span = scaled.max() - low
    return scaled.mapIndexed { index, value ->
        (if (values.size == 1) .5 else index.toDouble() / (values.size - 1)) to (if (span == 0.0) .5 else 1 - (value - low) / span)
    }
}
/** Equally-spaced samples, physical left to right. No time axis, interpolation, selection or loading. */
@Composable
fun Sparkline(values: List<Double>, summary: String, modifier: Modifier = Modifier, height: Dp = 64.dp,
    lineWidth: Dp = 3.dp, color: Color? = null) {
    require(height.value.isFinite() && height > 0.dp && lineWidth.value.isFinite() && lineWidth > 0.dp && height > lineWidth)
    val points = normalizedSparkline(values)
    val ink = color ?: FoundryTheme.tokens.colors.accent.color
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = summary }) {
        val stroke = lineWidth.toPx()
        val inset = minOf(stroke / 2, size.width / 2, size.height / 2)
        val coordinates = points.map { (x, y) -> Offset(inset + x.toFloat() * (size.width - 2 * inset).coerceAtLeast(0f),
            inset + y.toFloat() * (size.height - 2 * inset).coerceAtLeast(0f)) }
        if (coordinates.size == 1) drawCircle(ink, inset, coordinates.single())
        else if (coordinates.isNotEmpty()) {
            val path = Path().apply { moveTo(coordinates.first().x, coordinates.first().y); coordinates.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
