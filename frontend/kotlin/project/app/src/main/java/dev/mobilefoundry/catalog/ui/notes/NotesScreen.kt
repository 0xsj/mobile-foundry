package dev.mobilefoundry.catalog.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.services.Note
import dev.mobilefoundry.query.QueryState
import dev.mobilefoundry.ui.QueryContent
import dev.mobilefoundry.ui.QueryCopy

/** The feature supplies domain copy, emptiness, and rows to reusable presentation. */
@Composable
fun NotesScreen(state: QueryState<List<Note>>, refresh: () -> Unit, cancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notes", style = MaterialTheme.typography.titleLarge)
        QueryContent(state, QueryCopy(
            idle = "Ready to load notes.", loading = "Loading notes…",
            refreshing = "Refreshing notes…", empty = "No notes yet.",
        ), refresh = refresh, cancel = cancel, isEmpty = { it.isEmpty() }) { notes ->
            notes.forEach { Text(it.title) }
        }
    }
}
