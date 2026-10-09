package dev.mobilefoundry.ui.components.navigation.breadcrumbs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.theme.FoundryTheme

data class BreadcrumbItem(val id: String, val label: String, val enabled: Boolean = true)

/** Ancestors are native actions; the last item is passive. Paths and localized narration belong to the host. */
@Composable
fun BreadcrumbTrail(items: List<BreadcrumbItem>, currentAccessibilityLabel: String, onActivate: (String) -> Unit,
    modifier: Modifier = Modifier) {
    require(items.map { it.id }.distinct().size == items.size)
    val t = FoundryTheme.tokens
    WrapLayout(modifier) {
        items.forEachIndexed { index, item -> key(item.id) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
                if (index > 0) Text("/", Modifier.clearAndSetSemantics {}, color = t.colors.inkSecondary.color)
                if (index == items.lastIndex) Text(item.label, Modifier.heightIn(min = t.shape.minimumInteractive).wrapContentHeight()
                    .semantics { contentDescription = currentAccessibilityLabel }, style = t.typography.label)
                else ActionButton({ onActivate(item.id) }, enabled = item.enabled, variant = ButtonVariant.QUIET) { Text(item.label) }
            }
        } }
    }
}
