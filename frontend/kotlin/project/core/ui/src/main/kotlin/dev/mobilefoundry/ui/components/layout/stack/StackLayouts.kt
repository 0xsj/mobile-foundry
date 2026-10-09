package dev.mobilefoundry.ui.components.layout.stack

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Eager native Column with theme stack spacing. Native ColumnScope preserves caller weight/alignment. */
@Composable
fun VerticalStack(modifier: Modifier = Modifier, spacing: Dp? = null, alignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit) {
    require(spacing == null || (spacing.value.isFinite() && spacing >= 0.dp))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing ?: FoundryTheme.tokens.space.stack), horizontalAlignment = alignment, content = content)
}

/** Eager native Row with theme inline spacing. Does not wrap or choose a width. */
@Composable
fun HorizontalStack(modifier: Modifier = Modifier, spacing: Dp? = null, alignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit) {
    require(spacing == null || (spacing.value.isFinite() && spacing >= 0.dp))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing ?: FoundryTheme.tokens.space.inline), verticalAlignment = alignment, content = content)
}
