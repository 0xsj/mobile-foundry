package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.product.connection.ConnectedSessionModel
import dev.mobilefoundry.services.VersionedNote
import dev.mobilefoundry.services.NoteWriteAction

@Composable
fun LibraryScreen(model: ConnectedSessionModel) {
    val session by model.controller.state.collectAsStateWithLifecycle()
    val state by model.notes.state.collectAsStateWithLifecycle()
    val liveStatus by model.liveStatus.collectAsStateWithLifecycle()
    var deleting by remember(session.epoch) { mutableStateOf<VersionedNote?>(null) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your library", style = MaterialTheme.typography.headlineSmall)
        if (model.controller.lease == null) { Text("Sign in from Account to view your notes."); return@Column }
        Row { Text(liveStatus, Modifier.weight(1f).testTag("live.status")); TextButton(onClick = model::reconnectLive, modifier = Modifier.testTag("live.reconnect")) { Text("Reconnect") } }
        if (state.isBusy) CircularProgressIndicator()
        state.failure?.let { Text(it.meta.message, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { model.notes.refresh() }, enabled = !state.isBusy) { Text(if (state.recoveryLoaded) "Refresh notes" else "Reload recovery") }
        state.cachedAtMilliseconds?.let { saved ->
            Text("Saved first page · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(saved))}. Read-only until refreshed.", Modifier.testTag("notes.cached"))
        }
        state.cacheFailure?.let { Text(it.meta.message, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = model.notes::forgetSavedRead, enabled = !state.isBusy, modifier = Modifier.testTag("notes.cache.remove")) { Text("Remove saved copy") }
        if (state.loaded && state.notes.isEmpty()) Text("No notes in this workspace.", Modifier.testTag("notes.empty"))
        state.notes.forEach { note ->
            Text(note.title)
            Row {
                TextButton(onClick = { model.notes.edit(note) }, enabled = state.canWrite, modifier = Modifier.testTag("notes.edit.${note.id}")) { Text("Edit") }
                TextButton(onClick = { deleting = note }, enabled = state.canWrite, modifier = Modifier.testTag("notes.delete.${note.id}")) { Text("Delete") }
            }
        }
        state.nextAfter?.let { cursor -> TextButton(onClick = { model.notes.refresh(cursor) }, enabled = !state.isBusy) { Text("Next page") } }
        state.pending?.let { pending ->
            Text("Unconfirmed change", style = MaterialTheme.typography.titleMedium)
            Text("Saved on this device. Check its receipt or retry this exact change; recovery never sends it automatically.",
                Modifier.testTag("notes.pending.${pending.commandId}"))
            if (pending.action != NoteWriteAction.DELETE) Text(pending.title)
            TextButton(onClick = { model.notes.recover(true) }, enabled = !state.isBusy) { Text("Check receipt") }
            TextButton(onClick = { model.notes.recover(false) }, enabled = !state.isBusy) { Text("Retry same change") }
        }
        HorizontalDivider()
        Text(if (state.editing == null) "New note" else "Edit note", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(state.title, model.notes::setTitle, label = { Text("Title") }, enabled = state.canWrite,
            modifier = Modifier.fillMaxWidth().testTag("notes.title"))
        Button(onClick = { focus.clearFocus(); model.notes.save() }, enabled = state.canWrite, modifier = Modifier.testTag("notes.save")) {
            Text(if (state.editing == null) "Create note" else "Save note")
        }
        if (state.editing != null) TextButton(onClick = { model.notes.newDraft() }, enabled = state.canWrite) { Text("New note") }
        state.receipt?.let { Text("Confirmed ${it.action.name.lowercase()}, version ${it.version}") }
    }
    deleting?.let { note -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this note?") },
        confirmButton = { TextButton(onClick = { model.notes.delete(note); deleting = null }) { Text("Delete note") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}
