package dev.mobilefoundry.ui.components.layout.wrap

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Small eager compositions with intrinsic child widths. Scrolling and stable keys belong to the host. */
@Composable
fun WrapLayout(modifier: Modifier = Modifier, horizontalSpacing: Dp? = null, verticalSpacing: Dp? = null,
    content: @Composable FlowRowScope.() -> Unit) {
    require(horizontalSpacing == null || (horizontalSpacing.value.isFinite() && horizontalSpacing >= 0.dp))
    require(verticalSpacing == null || (verticalSpacing.value.isFinite() && verticalSpacing >= 0.dp))
    val t = FoundryTheme.tokens
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(horizontalSpacing ?: t.space.inline),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing ?: t.space.inline), content = content)
}
