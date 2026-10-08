package dev.mobilefoundry.ui.components.display.expandabletext

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Caller-controlled disclosure for known long copy; no overflow measurement or internal expansion state. */
@Composable
fun ExpandableText(text: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    moreLabel: String, lessLabel: String, modifier: Modifier = Modifier, collapsedLines: Int = 3) {
    require(collapsedLines > 0)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.inline)) {
        Text(text, maxLines = if (expanded) Int.MAX_VALUE else collapsedLines, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { onExpandedChange(!expanded) }) { Text(if (expanded) lessLabel else moreLabel) }
    }
}
