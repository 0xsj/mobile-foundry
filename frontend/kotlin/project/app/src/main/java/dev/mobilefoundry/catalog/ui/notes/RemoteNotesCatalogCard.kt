package dev.mobilefoundry.catalog.ui.notes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.services.VersionedNote
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader

@Composable fun RemoteNotesCatalogCard(model: RemoteNotesCatalogModel) {
    val state = model.state.collectAsStateWithLifecycle().value
    val title = remember { TextFieldState() };var deleting by remember { mutableStateOf<VersionedNote?>(null) }
    LaunchedEffect(state.title) { if (title.text.toString() != state.title) title.edit { replace(0,length,state.title) } }
    LaunchedEffect(title,model) { snapshotFlow { title.text.toString() }.collect { model.setTitle(it) } }
    Card {
        SectionHeader("Remote notes","Versioned writes in the selected account. Showing the first 20 notes.")
        ActionButton(model::refresh,variant = ButtonVariant.SECONDARY,isBusy = state.isBusy,enabled = !state.isBusy) { Text("Refresh notes") }
        LabeledTextField("Title",title,Modifier.fillMaxWidth(),enabled = state.canWrite)
        ActionButton({ model.setTitle(title.text.toString());model.save() },enabled = state.canWrite) { Text(if (state.editing == null) "Create remote note" else "Save remote note") }
        if (state.editing != null) ActionButton(model::newDraft,variant = ButtonVariant.QUIET,enabled = state.canWrite) { Text("New draft") }
        state.pending?.let { command ->
            Text("Unconfirmed command: ${command.commandId}")
            ActionButton({ model.recover(true) },variant = ButtonVariant.SECONDARY,enabled = !state.isBusy) { Text("Check write receipt") }
            ActionButton({ model.recover(false) },variant = ButtonVariant.QUIET,enabled = !state.isBusy) { Text("Retry original write") }
        }
        state.receipt?.let { Text("Confirmed ${it.action.wire}, version ${it.version}") }
        state.failure?.let { Text(it.meta.message) }
        state.notes.forEach { note -> key(note.id) {
            Text(note.title);Text("Version ${note.version}")
            Row {
                ActionButton({ model.edit(note) },variant = ButtonVariant.QUIET,enabled = state.canWrite) { Text("Edit") }
                ActionButton({ deleting = note },variant = ButtonVariant.DESTRUCTIVE,enabled = state.canWrite) { Text("Delete") }
            }
        } }
        if (state.hasMore) Text("More notes exist. The repository supports paged reads.")
    }
    deleting?.let { note -> AlertDialog(onDismissRequest = { deleting = null },title = { Text("Delete this remote note?") },
        confirmButton = { TextButton({ model.delete(note);deleting = null }) { Text("Delete") } },dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }) }
}
