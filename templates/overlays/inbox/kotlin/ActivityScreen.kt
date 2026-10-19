package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.product.connection.ConnectedSessionModel
import dev.mobilefoundry.services.InboxNotification

@Composable
fun ActivityScreen(model: ConnectedSessionModel, onOpen: ((InboxNotification) -> Unit)? = null) {
    val session by model.controller.state.collectAsStateWithLifecycle()
    val state by model.inbox.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account inbox", style = MaterialTheme.typography.headlineSmall)
        if (model.controller.lease == null) {
            Text("Sign in from Account to view your inbox."); return@Column
        }
        // Observe the public snapshot as well as the deliberately private lease.
        key(session.epoch) {
            state.unreadCount?.let {
                Text("$it unread notifications", Modifier.testTag("inbox.count"))
                Text("Showing ${state.items.size} notifications.", Modifier.testTag("inbox.loaded"))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Unread only", Modifier.weight(1f))
                Switch(state.unreadOnly, model.inbox::chooseUnreadOnly, enabled = !state.busy, modifier = Modifier.testTag("inbox.filter"))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Media notifications", Modifier.weight(1f))
                Switch(state.mediaEnabled ?: false, model.inbox::setMediaEnabled, enabled = model.inbox.canEdit, modifier = Modifier.testTag("inbox.media"))
            }
            TextButton(onClick = model.inbox::refresh, enabled = !state.busy, modifier = Modifier.testTag("inbox.refresh")) { Text("Refresh inbox") }
            if (state.busy) CircularProgressIndicator()
            if (state.needsRefresh) Text("Refresh to confirm server state before another change.", Modifier.testTag("inbox.recovery"))
            state.failure?.let { Text(it.meta.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox.failure")) }
            if (state.nextCursor != null) TextButton(onClick = model.inbox::loadMore, enabled = model.inbox.canLoadMore, modifier = Modifier.testTag("inbox.more")) { Text("Load more notifications") }
            if (state.items.size >= 200) Text("Showing up to 200 notices. Refresh for the newest items.")
            if (state.items.isEmpty() && state.unreadCount != null && !state.busy) {
                Text(if (state.unreadOnly) "You're all caught up." else "Your account inbox is empty.", Modifier.testTag("inbox.empty"))
            }
            state.items.forEach { item ->
                HorizontalDivider()
                Text("Upload ready", style = MaterialTheme.typography.titleMedium)
                Text("Your file finished uploading.")
                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date.from(item.createdAt)))
                Text(if (item.isUnread) "Unread" else "Read", Modifier.testTag("inbox.state.${item.id}"))
                if (onOpen != null) TextButton(onClick = { onOpen(item) }, modifier = Modifier.testTag("inbox.open.${item.id}")) { Text("Open file") }
                if (item.isUnread) TextButton(onClick = { model.inbox.markRead(item) }, enabled = model.inbox.canEdit,
                    modifier = Modifier.testTag("inbox.read.${item.id}")) { Text("Mark read") }
            }
            Text("Completed uploads appear after delivery. Media preferences affect future deliveries. Refresh to see new activity.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
