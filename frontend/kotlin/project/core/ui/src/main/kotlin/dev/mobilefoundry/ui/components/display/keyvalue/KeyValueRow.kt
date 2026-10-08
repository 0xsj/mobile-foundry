package dev.mobilefoundry.ui.components.display.keyvalue

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive supplied detail copy. Long values wrap, and larger fonts use a stacked layout. */
@Composable
fun KeyValueRow(title: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    val t = FoundryTheme.tokens
    val copy: @Composable () -> Unit = {
        Text(title, color = t.colors.inkSecondary.color)
        Text(value, style = t.typography.label)
    }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        if (LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(t.space.inline)) { copy() }
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(t.space.stack),
            verticalArrangement = Arrangement.spacedBy(t.space.inline), maxItemsInEachRow = 2) { copy() }
        if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
    }
}
