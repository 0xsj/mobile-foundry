package dev.mobilefoundry.ui.components.layout.container

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Centered readable bounds including page insets. The caller owns scrolling and system insets. */
@Composable
fun ContentContainer(modifier: Modifier = Modifier, maximumWidth: Dp = 720.dp,
    contentPadding: PaddingValues? = null, content: @Composable ColumnScope.() -> Unit) {
    require(maximumWidth.value.isFinite() && maximumWidth > 0.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maximumWidth).fillMaxWidth()
            .padding(contentPadding ?: PaddingValues(FoundryTheme.tokens.space.page)), content = content)
    }
}
