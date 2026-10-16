package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobilefoundry.product.notes.*
import dev.mobilefoundry.services.ObservableNotesRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import dev.mobilefoundry.services.Note
import dev.mobilefoundry.kernel.publicInfo
import dev.mobilefoundry.query.*
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.feedback.mutation.MutationFeedback
import dev.mobilefoundry.ui.components.feedback.query.QueryContent
import dev.mobilefoundry.ui.components.feedback.query.QueryCopy
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.layout.divider.SectionDivider
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.overlays.dialog.ConfirmationDialog
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun LibraryScreen(repository: ObservableNotesRepository? = null) {
    val context = LocalContext.current.applicationContext
    val source = remember(context, repository) { repository ?: NotesComposition.repository(context) }
    val model: LocalNotesViewModel = viewModel(key = "local-notes") {
        LocalNotesViewModel(source, NotesComposition::unexpected)
    }
    val scope = rememberCoroutineScope()
    val reader = remember(source, scope) { LiveNotesReader(scope, source, onUnexpectedError = NotesComposition::unexpected) }
    val state by model.state.collectAsStateWithLifecycle()
    val observed by reader.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, reader, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.refresh(); reader.start()
            try { awaitCancellation() } finally { reader.pause(); model.stop() }
        }
    }
    DisposableEffect(model, reader) { onDispose { reader.pause(); model.stop() } }
    LibraryNotesContent(state, model, reader, observed.query.value?.size, observed.query.failure?.publicInfo()?.meta?.message)
}
@Composable
private fun LibraryNotesContent(state: LocalNotesState, model: LocalNotesViewModel, reader: LiveNotesReader,
    observedCount: Int?, observationError: String?, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }; var wasFocused by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(model) {
        // Observation never writes an older emitted String back into native editing state.
        snapshotFlow { model.titleField.text.toString() }.collect { model.observeTitle() }
    }
    fun blur() { focus.clearFocus(); keyboard?.hide() }
    fun save() { if (model.save() != null) blur() else if (model.state.value.titleError != null) requester.requestFocus() }
    VerticalStack(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), spacing = t.space.section) {
        Card(role = SurfaceRole.FLOATING) {
            SectionHeader("Library", "Notes saved on this device")
            Text("No account or network is needed. Unsaved drafts are temporary.", style = t.typography.caption)
            Text(if (observedCount == null) "Reading local collection…" else "Live collection: $observedCount notes", style = t.typography.caption)
            observationError?.let {
                Text(it, style = t.typography.caption)
                ActionButton({ reader.restart() }, variant = ButtonVariant.QUIET) { Text("Restart observation") }
            }
        }
        Card {
            SectionHeader(if (state.editingId == null) "Create a note" else "Edit note", "Save a title of up to 200 Unicode characters.")
            LabeledTextField("Title", model.titleField,
                Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { if (wasFocused && !it.isFocused) model.blurTitle(); wasFocused = it.isFocused },
                help = "Unsaved drafts are temporary.", error = state.titleError, enabled = state.canAct,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), onKeyboardAction = { save() },
                inputTransformation = InputTransformation { if (!model.state.value.canAct) revertAllChanges() })
            ActionButton(::save, isBusy = state.mutation.isSubmitting, enabled = state.canAct) { Text(if (state.editingId == null) "Create note" else "Save changes") }
            if (state.editingId != null) ActionButton({ model.newNote() }, variant = ButtonVariant.QUIET, enabled = state.canAct) { Text("New note") }
            MutationFeedback(state.mutation, "Saving local change…") { Text(it, style = t.typography.caption) }
        }
        Card {
            SectionHeader("Saved notes", "Edits keep their position. Delete removes only the selected note.")
            QueryContent(state.query, QueryCopy(idle = "Ready to load notes.", loading = "Loading saved notes…",
                refreshing = "Reloading saved notes…", empty = "No saved notes yet."), { model.refresh() }, model::stop, isEmpty = { it.isEmpty() }) { notes ->
                notes.forEach { note ->
                    key(note.id) {
                        ListRow(note.title, trailing = {
                            Column {
                                ActionButton({ model.edit(note); requester.requestFocus() }, Modifier.semantics { contentDescription = "Edit ${note.title}" },
                                    variant = ButtonVariant.QUIET, enabled = state.canAct) { Text("Edit") }
                                ActionButton({ blur(); deleteTarget = note }, Modifier.semantics { contentDescription = "Delete ${note.title}" },
                                    variant = ButtonVariant.DESTRUCTIVE, enabled = state.canAct) { Text("Delete") }
                            }
                        })
                        SectionDivider()
                    }
                }
            }
        }
    }
    // Capture the target value before native dismissal clears presentation state.
    deleteTarget?.let { note ->
        ConfirmationDialog("Delete this note?", "“${note.title}” will be removed from this device.",
            true, { deleteTarget = null }, "Delete note", "Keep note", { model.delete(note) }, destructive = true)
    }
}
