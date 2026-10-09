package dev.mobilefoundry.ui.components.layout.divider

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class DividerAxis { HORIZONTAL, VERTICAL }

/** Decorative separator. Vertical use requires bounded host height; no interaction or implied heading. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier, axis: DividerAxis = DividerAxis.HORIZONTAL, inset: Dp = 0.dp,
    thickness: Dp = 1.dp, color: Color? = null) {
    require(inset.value.isFinite() && inset >= 0.dp && thickness.value.isFinite() && thickness > 0.dp)
    val decoration = modifier.clearAndSetSemantics { }
    val ink = color ?: FoundryTheme.tokens.colors.line.color
    if (axis == DividerAxis.HORIZONTAL) HorizontalDivider(decoration.padding(horizontal = inset), thickness, ink)
    else VerticalDivider(decoration.padding(vertical = inset), thickness, ink)
}
