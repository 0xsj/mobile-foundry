package dev.mobilefoundry.ui.components.navigation.pagination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Controlled one-based pages; the feature owns page copy, totals and empty/loading policy. */
@Composable
fun PaginationBar(page: Int, totalPages: Int, pageLabel: String, previousLabel: String, nextLabel: String,
    onPageChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    require(totalPages >= 1 && page in 1..totalPages)
    val t = FoundryTheme.tokens
    WrapLayout(modifier) {
        ActionButton({ if (enabled && page > 1) onPageChange(page - 1) }, variant = ButtonVariant.SECONDARY, enabled = enabled && page > 1) { Text(previousLabel) }
        Text(pageLabel, Modifier.heightIn(min = t.shape.minimumInteractive).wrapContentHeight(), style = t.typography.caption)
        ActionButton({ if (enabled && page < totalPages) onPageChange(page + 1) }, variant = ButtonVariant.SECONDARY, enabled = enabled && page < totalPages) { Text(nextLabel) }
    }
}
