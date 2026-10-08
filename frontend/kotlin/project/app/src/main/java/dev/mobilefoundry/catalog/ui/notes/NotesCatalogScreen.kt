package dev.mobilefoundry.catalog.ui.notes

import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobilefoundry.catalog.composition.*

@Composable
fun NotesCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var provider by rememberSaveable { mutableStateOf(NotesProvider.MEMORY) }
    var scenario by rememberSaveable { mutableStateOf(NotesScenario.CONTENT) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Notes service seam", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotesProvider.entries.forEach { value ->
                FilterChip(selected = provider == value, onClick = { provider = value }, label = { Text(value.label) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotesScenario.entries.forEach { value ->
                FilterChip(selected = scenario == value, onClick = { scenario = value }, label = { Text(value.label) })
            }
        }
        Text("Memory and HTTP use the same screen. HTTP responses are injected locally.")
        key(provider, scenario) { NotesExample(provider, scenario) }
    }
}

@Composable
private fun NotesExample(provider: NotesProvider, scenario: NotesScenario) {
    val model: NotesViewModel = viewModel(key = "notes-$provider-$scenario") {
        NotesViewModel(NotesComposition.service(provider, scenario)) { Log.e("FoundryNotes", "Unexpected notes error", it) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.refresh() }
    DisposableEffect(model) { onDispose { model.cancel() } }
    NotesScreen(state, refresh = { model.refresh() }, cancel = model::cancel)
}
