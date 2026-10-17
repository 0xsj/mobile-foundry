package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.product.connection.WorkspaceModel
import dev.mobilefoundry.session.SessionController

@Composable
fun WorkspaceScreen(model: WorkspaceModel, session: SessionController) {
    val status by model.state.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    var rename by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { model.refresh() }
    LaunchedEffect(status.current?.version) { rename = status.current?.account?.name ?: "" }
    LaunchedEffect(sessionState.epoch) { rename = ""; name = "" }
    DisposableEffect(model) { onDispose { model.cancel() } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Manage workspaces", style = MaterialTheme.typography.headlineSmall)
        if (status.busy) CircularProgressIndicator()
        status.failure?.let { Text(it.meta.message, Modifier.testTag("workspace.error"), color = MaterialTheme.colorScheme.error) }
        status.message?.let { Text(it, Modifier.testTag("workspace.message")) }
        Text("Current workspace", style = MaterialTheme.typography.titleMedium)
        status.current?.let { Text("Current: ${it.account.name}", Modifier.testTag("workspace.current")) }
        TextButton(onClick = { focus.clearFocus(); model.refresh() }, enabled = !status.busy, modifier = Modifier.testTag("workspace.refresh")) { Text("Refresh current workspace") }
        OutlinedTextField(rename, { rename = it }, label = { Text("Workspace name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("workspace.rename-name"))
        Button(onClick = { focus.clearFocus(); model.rename(rename) }, enabled = model.canRename, modifier = Modifier.testTag("workspace.rename")) { Text("Rename workspace") }
        HorizontalDivider(); Text("Create a workspace", style = MaterialTheme.typography.titleMedium)
        Text("If an earlier create was interrupted, review Workspaces before creating another. Creation leaves selection unchanged.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("New workspace name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("workspace.create-name"))
        Button(onClick = { focus.clearFocus(); model.create(name) }, enabled = model.canCreate, modifier = Modifier.testTag("workspace.create")) { Text("Create workspace") }
        status.created?.let {
            Text("Created: ${it.account.name}", Modifier.testTag("workspace.created"))
            TextButton(onClick = { name = ""; model.anotherCreate() }, enabled = !status.busy) { Text("Prepare another workspace") }
        }
        if (status.createUncertain) {
            Text(WorkspaceModel.interruptedCreate, Modifier.testTag("workspace.uncertain"))
            TextButton(onClick = { focus.clearFocus(); model.review() }, enabled = !status.busy, modifier = Modifier.testTag("workspace.review")) { Text("Review workspaces") }
            status.reviewAccounts.forEach { Text(it.name) }
            status.nextCursor?.let { cursor -> TextButton(onClick = { model.review(cursor) }, enabled = !status.busy) { Text("Review next page") } }
            Text("Pages are live observations. They cannot prove whether a particular create committed.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { model.allowNewCreate() }, enabled = status.reviewed && !status.busy, modifier = Modifier.testTag("workspace.allow-create")) { Text("I reviewed workspaces; allow a new create") }
        }
    }
}
