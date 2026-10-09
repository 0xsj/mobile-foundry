package dev.mobilefoundry.ui.components.navigation.tablesortheader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant

enum class TableSortOrder { ASCENDING, DESCENDING }

/** One native action; the caller chooses the next order and supplies localized sort-state narration. */
@Composable
fun TableSortHeader(title: String, order: TableSortOrder?, accessibilityValue: String, onSort: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    ActionButton(onSort, modifier.semantics { stateDescription = accessibilityValue }, variant = ButtonVariant.QUIET, enabled = enabled) {
        Text(title, Modifier.weight(1f, fill = false))
        Text(when (order) { TableSortOrder.ASCENDING -> " ↑"; TableSortOrder.DESCENDING -> " ↓"; null -> " ↕" }, Modifier.clearAndSetSemantics {})
    }
}
