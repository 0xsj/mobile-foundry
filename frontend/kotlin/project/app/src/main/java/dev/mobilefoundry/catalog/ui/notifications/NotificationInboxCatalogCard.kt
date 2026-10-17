package dev.mobilefoundry.catalog.ui.notifications

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.services.InboxNotification
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.display.countbadge.CountBadge
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.patterns.notificationrow.NotificationRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable fun NotificationInboxCatalogCard(model: NotificationInboxCatalogModel) {
    val state = model.state.collectAsStateWithLifecycle().value
    val t = FoundryTheme.tokens
    var selected by remember { mutableStateOf<InboxNotification?>(null) }
    val time = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    Card {
        SectionHeader("Account inbox", "Upload a file, then refresh to see its upload-ready notice.", actions = {
            state.unreadCount?.let { CountBadge("$it", "$it unread account notifications", modifier = Modifier.testTag("go.inbox.count")) }
        })
        ToggleField("Unread only", state.unreadOnly, model::chooseUnreadOnly, enabled = !state.busy)
        ToggleField("Media notifications", state.mediaEnabled ?: false, model::setMediaEnabled, enabled = model.canEdit, modifier = Modifier.testTag("go.inbox.media"))
        ActionButton(model::refresh, variant = ButtonVariant.SECONDARY, enabled = !state.busy, modifier = Modifier.testTag("go.inbox.refresh")) { Text("Refresh inbox") }
        if (state.busy) LinearProgressIndicator()
        if (state.needsRefresh) Text("Refresh to confirm server state before another change.", style = t.typography.caption)
        state.failure?.let { Text(it.meta.message, modifier = Modifier.testTag("go.inbox.failure"), style = t.typography.caption) }
        if (state.items.isEmpty() && state.unreadCount != null && !state.busy) {
            EmptyState(if (state.unreadOnly) "You're all caught up" else "Your account inbox is empty", "Completed uploads appear here after delivery. Media preferences affect future deliveries.")
        }
        state.items.forEach { item -> key(item.id) {
            NotificationRow("Upload ready", "Your file finished uploading.", time.format(item.createdAt), if (item.isUnread) "Unread" else "Read", item.isUnread,
                "Upload ready, ${if (item.isUnread) "Unread" else "Read"}", onOpen = { selected = item; model.markRead(item) }, enabled = !state.busy,
                actions = { if (item.isUnread) ActionButton({ model.markRead(item) }, variant = ButtonVariant.QUIET, enabled = model.canEdit, modifier = Modifier.testTag("go.inbox.read.${item.id}")) { Text("Mark read") } })
        } }
        if (state.nextCursor != null) ActionButton(model::loadMore, variant = ButtonVariant.QUIET, enabled = model.canLoadMore) { Text("Load more notifications") }
        if (state.items.size >= 200) Text("Showing up to 200 notices. Refresh for the newest items.", style = t.typography.caption)
    }
    SheetPanel("Upload ready", selected != null, { selected = null }, "Close notification") {
        selected?.let { Text("Your upload completed."); Text("File ID: ${it.fileId}", style = t.typography.code)
            Text("The file may have changed or been deleted since this notice arrived.", style = t.typography.caption) }
    }
}
