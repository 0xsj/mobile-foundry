package dev.mobilefoundry.catalog.ui.session

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.catalog.ui.notes.LocalNotesViewModel
import dev.mobilefoundry.catalog.ui.notes.LiveNotesReader
import dev.mobilefoundry.catalog.ui.notes.LiveNotesReaderCard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import dev.mobilefoundry.data.AccountNotesRepositories
import dev.mobilefoundry.services.Note
import dev.mobilefoundry.session.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.forms.button.*
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.feedback.mutation.MutationFeedback
import dev.mobilefoundry.ui.components.feedback.query.QueryContent
import dev.mobilefoundry.ui.components.feedback.query.QueryCopy
import dev.mobilefoundry.ui.components.overlays.dialog.ConfirmationDialog
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Host owns scrolling; these editors are discarded with this exact session lease. */
@Composable fun AccountAccessPanel(session: SessionController, lease: SessionLease, repositories: AccountNotesRepositories) {
    val scope = rememberCoroutineScope()
    val model = remember(lease) { LocalNotesViewModel(repositories.bind(session, lease)) { Log.e("AccountNotes", "Unexpected note error", it) } }
    val probe = remember(lease) { AuthenticatedProbeModel(scope, session, lease) }
    val reader = remember(lease) { LiveNotesReader(scope, repositories.repository(lease.scope), admitted = { session.isCurrent(lease) }) { Log.e("AccountNotes", "Unexpected observation error", it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by model.state.collectAsStateWithLifecycle()
    val probeState by probe.state.collectAsStateWithLifecycle()
    var response by remember { mutableStateOf(ProbeResponse.SUCCESS) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    fun blur() { focus.clearFocus(); keyboard?.hide() }
    fun save() { if (model.save() != null) blur() }
    LaunchedEffect(model) { model.refresh()?.join() }
    LaunchedEffect(model) { snapshotFlow { model.titleField.text.toString() }.collect { model.observeTitle() } }
    LaunchedEffect(reader, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            reader.start()
            try { awaitCancellation() } finally { reader.pause() }
        }
    }
    DisposableEffect(model, probe, reader) { onDispose { deleteTarget = null; model.shutdown(); probe.shutdown(); reader.shutdown() } }
    Card {
        SectionHeader("Account notes", lease.scope.accountId)
        Text("Saved notes belong to this account on this device. Signing out keeps them; switching accounts discards the editor draft.", style = t.typography.caption)
        LabeledTextField("Account note title", model.titleField, Modifier.fillMaxWidth(), error = state.titleError, enabled = state.canAct,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), onKeyboardAction = { save() },
            inputTransformation = InputTransformation { if (!model.state.value.canAct) revertAllChanges() })
        ActionButton(::save, enabled = state.canAct, isBusy = state.mutation.isSubmitting) { Text(if (state.editingId == null) "Create account note" else "Save account note") }
        if (state.editingId != null) ActionButton(model::newNote, variant = ButtonVariant.QUIET, enabled = state.canAct) { Text("New account note") }
        MutationFeedback(state.mutation, "Saving account change…") { Text(it, style = t.typography.caption) }
        QueryContent(state.query, QueryCopy(idle = "Ready to load this account.", loading = "Loading account notes…", refreshing = "Reloading account notes…", empty = "No notes in this account."),
            { model.refresh() }, model::stop, isEmpty = { it.isEmpty() }) { notes ->
            notes.forEach { note -> key(note.id) {
                ListRow(note.title, trailing = {
                    Column {
                        ActionButton({ model.edit(note) }, variant = ButtonVariant.QUIET, enabled = state.canAct) { Text("Edit") }
                        ActionButton({ blur(); deleteTarget = note }, variant = ButtonVariant.DESTRUCTIVE, enabled = state.canAct) { Text("Delete") }
                    }
                })
            } }
        }
    }
    Card {
        SectionHeader("Authenticated HTTP", "Local response fixture. No server connection.")
        SelectField("Probe response", ProbeResponse.entries, response, { response = it }, enabled = !probeState.isBusy, label = { it.label })
        ActionButton({ probe.run(response) }, enabled = !probeState.isBusy, isBusy = probeState.isBusy) { Text("Send authenticated probe") }
        probeState.report?.let { Text(it, style = t.typography.caption) }
        Text("Unauthorized returns one failure. Use the lifecycle controls to renew explicitly.", style = t.typography.caption)
    }
    LiveNotesReaderCard("Account live reader", reader)
    deleteTarget?.let { note ->
        ConfirmationDialog("Delete this account note?", "“${note.title}” will be removed from this account on this device.", true,
            { deleteTarget = null }, "Delete note", "Keep note", { model.delete(note) }, destructive = true)
    }
}
