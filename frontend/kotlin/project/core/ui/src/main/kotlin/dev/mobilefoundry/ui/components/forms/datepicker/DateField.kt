package dev.mobilefoundry.ui.components.forms.datepicker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Calendar dates encoded as UTC midnight milliseconds, not instants in a local time zone.
 * Opening starts a modal draft; only Confirm commits it to the caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(title: String, selectedDateMillis: Long?, onDateChange: (Long) -> Unit,
    confirmLabel: String, cancelLabel: String, placeholder: String,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    var presented by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) presented = false }
    val formatted = selectedDateMillis?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it))
    } ?: placeholder
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.inline)) {
        Text(title, style = FoundryTheme.tokens.typography.label)
        OutlinedButton(onClick = { presented = true }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title }) { Text(formatted) }
    }
    if (presented && enabled) {
        val draft = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(onDismissRequest = { presented = false },
            confirmButton = { TextButton(enabled = enabled && draft.selectedDateMillis != null, onClick = {
                if (enabled) draft.selectedDateMillis?.let(onDateChange); presented = false
            }) { Text(confirmLabel) } },
            dismissButton = { TextButton(onClick = { presented = false }) { Text(cancelLabel) } }) {
            DatePicker(draft, title = { Text(title, modifier = Modifier.padding(FoundryTheme.tokens.space.page)) })
        }
    }
}
