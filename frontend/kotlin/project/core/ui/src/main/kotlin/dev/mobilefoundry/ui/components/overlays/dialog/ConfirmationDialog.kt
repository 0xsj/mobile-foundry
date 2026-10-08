package dev.mobilefoundry.ui.components.overlays.dialog

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Cancel, Back, and outside dismissal never invoke onConfirm. */
@Composable
fun ConfirmationDialog(title: String, message: String, isPresented: Boolean, onDismissRequest: () -> Unit,
    confirmLabel: String, cancelLabel: String, onConfirm: () -> Unit,
    modifier: Modifier = Modifier, destructive: Boolean = false) {
    if (isPresented) AlertDialog(modifier = modifier, onDismissRequest = onDismissRequest,
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismissRequest(); onConfirm() },
            colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(cancelLabel) } })
}
