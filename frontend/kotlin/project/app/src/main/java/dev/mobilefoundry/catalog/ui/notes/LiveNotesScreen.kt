package dev.mobilefoundry.catalog.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobilefoundry.catalog.composition.LocalNotesProvider
import dev.mobilefoundry.query.*
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.forms.button.*
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.feedback.mutation.MutationFeedback
import dev.mobilefoundry.ui.components.feedback.query.*
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.awaitCancellation

@Composable fun LiveNotesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, databaseName: String = "live-notes.sqlite") {
    var provider by rememberSaveable { mutableStateOf(LocalNotesProvider.DEVICE) }
    val context = LocalContext.current.applicationContext
    val catalog: LiveNotesCatalogModel = viewModel { LiveNotesCatalogModel(context, databaseName) }
    val source = catalog.source(provider)
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Live notes", style = FoundryTheme.tokens.typography.title)
        key(source) { LiveNotesContent(source, provider, { provider = it }, Modifier.weight(1f)) }
    }
}
@Composable private fun LiveNotesContent(source: LiveNotesSource, provider: LocalNotesProvider, onProvider: (LocalNotesProvider) -> Unit, modifier: Modifier) {
    val state by source.writer.state.collectAsStateWithLifecycle()
    var secondPaused by remember(source) { mutableStateOf(source.secondPaused) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    val t = FoundryTheme.tokens
    fun blur() { focus.clearFocus(); keyboard?.hide() }
    fun save() { if (source.writer.save() != null) blur() }
    LaunchedEffect(source) { snapshotFlow { source.writer.titleField.text.toString() }.collect { source.writer.observeTitle() } }
    LaunchedEffect(source, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            source.resume()
            try { awaitCancellation() } finally { source.pause() }
        }
    }
    DisposableEffect(source) { onDispose { source.pause() } }
    VerticalStack(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), spacing = t.space.section) {
        Card(role = SurfaceRole.FLOATING) {
            SectionHeader("Two live readers", "Write once. Independent readers observe current local data.")
            SelectField("Storage", LocalNotesProvider.entries, provider, { if (!state.isBusy) { blur(); source.pause(); onProvider(it) } }, enabled = !state.isBusy, label = { it.label })
            Text(if (provider == LocalNotesProvider.DEVICE) "Separate SQLite instances coordinate writes in this process. This gallery uses its own database."
                else "One Memory repository feeds both readers. It remains in this route until the gallery closes.", style = t.typography.caption)
        }
        Card {
            SectionHeader("Writer", "Use this editor while watching the readers below.")
            LabeledTextField("Live note title", source.writer.titleField, Modifier.fillMaxWidth(), error = state.titleError, enabled = state.canAct,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), onKeyboardAction = { save() },
                inputTransformation = InputTransformation { if (!source.writer.state.value.canAct) revertAllChanges() })
            ActionButton(::save, enabled = state.canAct, isBusy = state.mutation.isSubmitting) { Text(if (state.editingId == null) "Create live note" else "Save live note") }
            if (state.editingId != null) ActionButton(source.writer::newNote, variant = ButtonVariant.QUIET, enabled = state.canAct) { Text("New live note") }
            MutationFeedback(state.mutation, "Saving…") { Text(it, style = t.typography.caption) }
            state.query.value.orEmpty().forEach { note -> key(note.id) {
                ListRow(note.title, trailing = { Column {
                    ActionButton({ source.writer.edit(note) }, Modifier.semantics { contentDescription = "Edit live note ${note.title}" }, variant = ButtonVariant.QUIET, enabled = state.canAct) { Text("Edit") }
                    ActionButton({ source.writer.delete(note) }, Modifier.semantics { contentDescription = "Delete live note ${note.title}" }, variant = ButtonVariant.DESTRUCTIVE, enabled = state.canAct) { Text("Delete") }
                } })
            } }
            state.query.failure?.let { Text(it.meta.message) }
            ActionButton({ source.writer.refresh() }, variant = ButtonVariant.QUIET, enabled = !state.isBusy) { Text("Reload writer") }
        }
        LiveNotesReaderCard("First reader", source.first)
        Card {
            ActionButton({ secondPaused = !secondPaused; source.secondPaused = secondPaused; if (secondPaused) source.second.pause() else source.second.start() }, variant = ButtonVariant.SECONDARY) {
                Text(if (secondPaused) "Resume second reader" else "Pause second reader")
            }
            LiveNotesReaderContent("Second reader", source.second)
        }
    }
}
@Composable fun LiveNotesReaderCard(title: String, reader: LiveNotesReader) { Card { LiveNotesReaderContent(title, reader) } }
@Composable private fun LiveNotesReaderContent(title: String, reader: LiveNotesReader) {
    val state by reader.state.collectAsStateWithLifecycle()
    SectionHeader(title, if (state.isWatching) "Watching local changes" else "Paused · last snapshot")
    QueryContent(state.query, QueryCopy(idle = "No ${title.lowercase()} snapshot.", loading = "Loading ${title.lowercase()}…", refreshing = "Resuming ${title.lowercase()}…",
        empty = "$title: 0 notes", refresh = "Restart ${title.lowercase()}", cancel = "Pause ${title.lowercase()}"), { reader.restart() }, reader::pause, isEmpty = { it.isEmpty() }) { notes ->
        Text("$title: ${notes.size} notes", style = FoundryTheme.tokens.typography.caption)
        notes.forEach { note -> key(note.id) { Text("$title: ${note.title}") } }
    }
}
