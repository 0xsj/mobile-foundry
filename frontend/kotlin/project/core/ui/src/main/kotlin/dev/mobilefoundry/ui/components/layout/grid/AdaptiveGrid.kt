package dev.mobilefoundry.ui.components.layout.grid

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlin.math.max

/** Eager, non-scrolling grid for small compositions; use native lazy grids for long feeds.
 * Requires bounded width and enough height (typically an outer vertical scroll). Caller keys retain identity.
 * Minimum item width grows with font scale; row height follows the tallest child. */
@Composable
fun AdaptiveGrid(modifier: Modifier = Modifier, minimumItemWidth: Dp = 160.dp, maximumColumns: Int = 3,
    spacing: Dp? = null, content: @Composable () -> Unit) {
    require(minimumItemWidth.value.isFinite() && minimumItemWidth > 0.dp && maximumColumns > 0)
    require(spacing == null || (spacing.value.isFinite() && spacing >= 0.dp))
    val gapDp = spacing ?: FoundryTheme.tokens.space.stack
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(constraints.hasBoundedWidth) { "AdaptiveGrid requires a bounded width" }
        val gap = gapDp.roundToPx()
        val minimum = minimumItemWidth.toPx() * fontScale
        val columns = ((constraints.maxWidth + gap) / (minimum + gap)).toInt().coerceIn(1, maximumColumns)
        val cell = ((constraints.maxWidth - (columns - 1) * gap) / columns).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(cell)) }
        val rows = IntArray((placeables.size + columns - 1) / columns)
        placeables.forEachIndexed { index, child -> rows[index / columns] = max(rows[index / columns], child.height) }
        val height = rows.sum() + max(0, rows.size - 1) * gap
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            var y = 0
            placeables.forEachIndexed { index, child ->
                child.placeRelative((index % columns) * (cell + gap), y)
                if (index % columns == columns - 1) y += rows[index / columns] + gap
            }
        }
    }
}
