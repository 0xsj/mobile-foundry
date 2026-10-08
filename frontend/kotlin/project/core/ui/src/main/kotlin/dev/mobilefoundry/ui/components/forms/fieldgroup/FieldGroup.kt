package dev.mobilefoundry.ui.components.forms.fieldgroup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Group-level feedback; native field focus, validation and per-field associations stay with the caller. */
@Composable
fun FieldGroup(title: String, modifier: Modifier = Modifier, help: String? = null, error: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Column(modifier.semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Text(title, style = t.typography.label, modifier = Modifier.semantics { heading() })
        content()
        if (error != null) Text(error, style = t.typography.caption, color = t.colors.crit.color,
            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
        else help?.let { Text(it, style = t.typography.caption, color = t.colors.inkSecondary.color) }
    }
}
