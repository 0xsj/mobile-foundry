package dev.mobilefoundry.ui.components.patterns.searchresultrow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One native open action with passive artwork/preview and independent sibling actions. No route/result ownership. */
@Composable
fun SearchResultRow(title: String, accessibilityLabel: String, onOpen: () -> Unit, modifier: Modifier = Modifier,
    detail: String? = null, enabled: Boolean = true, leading: @Composable () -> Unit = {}, preview: @Composable () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Column(Modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive)
            .clickable(enabled = enabled, role = Role.Button) { if (enabled) onOpen() }
            .semantics { contentDescription = accessibilityLabel },
            verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                ListRow(title, subtitle = detail, leading = { Box(Modifier.clearAndSetSemantics {}) { leading() } })
                preview()
            }
        }
        actions()
    }
}
