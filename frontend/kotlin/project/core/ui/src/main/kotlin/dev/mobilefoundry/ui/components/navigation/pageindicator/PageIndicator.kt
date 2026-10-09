package dev.mobilefoundry.ui.components.navigation.pageindicator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive small-carousel position. Selection and localized copy belong to the host. */
@Composable
fun PageIndicator(count: Int, selected: Int, label: String, modifier: Modifier = Modifier) {
    require(count in 1..20 && selected in 0 until count)
    val t = FoundryTheme.tokens
    Row(modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(width = if (index == selected) 18.dp else 6.dp, height = 6.dp)
                .background(if (index == selected) t.colors.accent.color else t.colors.lineStrong.color, CircleShape))
        }
    }
}
