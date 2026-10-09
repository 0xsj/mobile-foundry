package dev.mobilefoundry.ui.components.patterns.searchsuggestionrow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import dev.mobilefoundry.ui.components.display.listrow.ListRow

/** One supplied search/history action with decorative artwork. Query/history ownership stays in the host. */
@Composable
fun SearchSuggestionRow(title: String, accessibilityLabel: String, onUse: () -> Unit, modifier: Modifier = Modifier,
    detail: String? = null, enabled: Boolean = true, leading: @Composable () -> Unit = {}) {
    ListRow(title, detail, modifier.clickable(enabled = enabled, role = Role.Button) { if (enabled) onUse() }
        .semantics { contentDescription = accessibilityLabel },
        leading = { Box(Modifier.clearAndSetSemantics {}) { leading() } },
        trailing = { Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↗" else "↖", Modifier.clearAndSetSemantics {}) })
}
