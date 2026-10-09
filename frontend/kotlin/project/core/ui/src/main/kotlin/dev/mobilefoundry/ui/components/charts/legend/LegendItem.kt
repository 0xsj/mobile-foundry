package dev.mobilefoundry.ui.components.charts.legend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class LegendMark { DOT, LINE, SQUARE }
/** Passive series copy and decorative mark. Supply meaning beyond color. */
@Composable
fun LegendItem(label: String, modifier: Modifier = Modifier, color: Color? = null, mark: LegendMark = LegendMark.DOT) {
    val t = FoundryTheme.tokens
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Box(Modifier.size(if (mark == LegendMark.LINE) 24.dp else 12.dp, if (mark == LegendMark.LINE) 4.dp else 12.dp)
            .background(color ?: t.colors.accent.color, RoundedCornerShape(if (mark == LegendMark.DOT) 6.dp else if (mark == LegendMark.LINE) 2.dp else 0.dp))
            .clearAndSetSemantics {})
        Text(label, style = t.typography.caption)
    }
}
