package dev.mobilefoundry.ui.components.forms.daterange

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.forms.datepicker.DateField
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Two independently committed UTC-encoded calendar dates. The caller owns ordering, bounds and validation. */
@Composable
fun DateRangeField(title: String, startDateMillis: Long?, endDateMillis: Long?, onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit, startLabel: String, endLabel: String, confirmLabel: String, cancelLabel: String,
    placeholder: String, modifier: Modifier = Modifier, help: String? = null, error: String? = null, enabled: Boolean = true) {
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Text(title, style = t.typography.label, modifier = Modifier.semantics { heading() })
        DateField(startLabel, startDateMillis, onStartChange, confirmLabel, cancelLabel, placeholder, enabled = enabled)
        DateField(endLabel, endDateMillis, onEndChange, confirmLabel, cancelLabel, placeholder, enabled = enabled)
        help?.let { Text(it, style = t.typography.caption, color = t.colors.inkSecondary.color) }
        error?.let { Text(it, style = t.typography.caption, color = t.colors.crit.color) }
    }
}
