package dev.mobilefoundry.ui.components.forms.timepicker

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

/** A wall-clock reading. No date, time zone, recurrence or duration is implied. */
data class ClockTime(val hour: Int, val minute: Int) {
    init { require(hour in 0..23 && minute in 0..59) }
}

/** Native time input with a disposable modal draft and explicit commit. Copy belongs to the caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(title: String, selection: ClockTime, onTimeChange: (ClockTime) -> Unit, valueLabel: String,
    confirmLabel: String, cancelLabel: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = FoundryTheme.tokens
    var presented by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) presented = false }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label)
        ActionButton({ presented = true }, variant = ButtonVariant.SECONDARY, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title; stateDescription = valueLabel }) { Text(valueLabel) }
    }
    if (presented && enabled) {
        val draft = rememberTimePickerState(selection.hour, selection.minute, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(onDismissRequest = { presented = false }, title = { Text(title) }, text = {
            // The native input can be wider than a compact dialog at large font scales.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.horizontalScroll(rememberScrollState())) { TimeInput(draft) }
            }
        }, confirmButton = {
            TextButton(enabled = enabled, onClick = { if (enabled) { onTimeChange(ClockTime(draft.hour, draft.minute)); presented = false } }) { Text(confirmLabel) }
        }, dismissButton = { TextButton({ presented = false }) { Text(cancelLabel) } })
    }
}
