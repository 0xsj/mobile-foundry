package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.countbadge.CountBadge
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.feedback.alert.InlineAlert
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.overlays.menu.ActionMenu
import dev.mobilefoundry.ui.components.overlays.menu.MenuAction
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.patterns.notificationrow.NotificationRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class InboxFilter(val label: String) { ALL("All"), UNREAD("Unread") }
/** English fixture copy; production localization belongs to the caller. */
internal fun inboxUpdateNoun(count: Int) = if (count == 1) "update" else "updates"
internal data class InboxNotice(val id: String, val title: String, val message: String, val time: String, val group: String, val artwork: String) {
    companion object { val all = listOf(InboxNotice("review", "Review is ready", "Your latest studio study is ready for a closer look.", "2 minutes ago", "Today", "✓"),
        InboxNotice("invite", "Workspace invitation", "A new place to gather ideas and collaborate.", "1 hour ago", "Today", "+"),
        InboxNotice("export", "Export complete", "Your image has been prepared for sharing.", "Yesterday", "Earlier", "↑"),
        InboxNotice("tools", "New editing tools", "Explore a few new ways to shape your next image.", "Monday", "Earlier", "≡")) }
}
/** Bounded local inbox. Read identities survive filters/archive; undo restores only the latest archived identity. */
internal data class NotificationValues(val filter: InboxFilter = InboxFilter.ALL, val read: List<String> = listOf("export"),
    val archived: List<String> = emptyList(), val undoID: String? = null, val opened: String? = null, val opens: Int = 0, val enabled: Boolean = true) {
    val active get() = InboxNotice.all.filter { it.id !in archived }
    val visible get() = active.filter { filter == InboxFilter.ALL || it.id !in read }
    val unreadCount get() = active.count { it.id !in read }
    val canMarkVisible get() = enabled && visible.any { it.id !in read }
    val canUndo get() = enabled && undoID != null && undoID in archived
    val openedNotice get() = InboxNotice.all.firstOrNull { it.id == opened }
    val canShowDetail get() = enabled && active.any { it.id == opened }
    fun chooseFilter(value: InboxFilter) = if (enabled) copy(filter = value) else this
    fun setRead(id: String, value: Boolean): NotificationValues {
        if (!enabled || active.none { it.id == id }) return this
        return copy(read = read.filterNot { it == id } + if (value) listOf(id) else emptyList())
    }
    fun markVisibleRead(): NotificationValues {
        if (!canMarkVisible) return this
        return visible.map { it.id }.fold(this) { values, id -> values.setRead(id, true) }
    }
    fun open(id: String) = if (enabled && active.any { it.id == id }) setRead(id, true).copy(opened = id, opens = opens + 1) else this
    fun archive(id: String) = if (enabled && active.any { it.id == id }) copy(archived = archived + id, undoID = id) else this
    fun undoArchive() = if (canUndo) copy(archived = archived.filterNot { it == undoID }, undoID = null) else this
    fun reset() = if (enabled) copy(filter = InboxFilter.ALL, read = listOf("export"), archived = emptyList(), undoID = null, opened = null) else this
}
@Composable
internal fun NotificationExamples(values: NotificationValues, onChange: (NotificationValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("An everyday inbox", subtitle = "Readable counts, independent row actions and grouped updates.")
        CountBadge("99+", "128 unread updates")
        NavLink("Open inbox preview", onPreview, subtitle = "Try read state, filters, archive and undo")
    }
    NotificationContent(values, onChange)
}
@Composable
internal fun NotificationPreviewScreen(values: NotificationValues, onChange: (NotificationValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Inbox preview", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("inbox-scroll").padding(20.dp)) { NotificationContent(values, onChange) }
    }
}
@Composable
internal fun NotificationContent(values: NotificationValues, onChange: (NotificationValues) -> Unit) {
    val t = FoundryTheme.tokens
    var showDetail by remember { mutableStateOf(false) }
    LaunchedEffect(values.canShowDetail) { if (!values.canShowDetail) showDetail = false }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable inbox actions", values.enabled, { onChange(values.copy(enabled = it)) })
            SectionHeader("Inbox", subtitle = "${values.visible.size} visible ${inboxUpdateNoun(values.visible.size)}", actions = {
                CountBadge("${values.unreadCount}", "${values.unreadCount} unread ${inboxUpdateNoun(values.unreadCount)} in inbox")
            })
            SelectField("Inbox filter", InboxFilter.entries, values.filter, { onChange(values.chooseFilter(it)) }, enabled = values.enabled, label = { it.label })
            ActionButton({ onChange(values.markVisibleRead()) }, variant = ButtonVariant.SECONDARY, enabled = values.canMarkVisible) { Text("Mark visible as read") }
            ActionButton({ onChange(values.reset()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Reset inbox") }
            Text("Updates opened: ${values.opens}", style = t.typography.caption)
            Text("Last opened: ${values.openedNotice?.title ?: "None"}", style = t.typography.caption)
        }
        values.undoID?.let { id -> InboxNotice.all.firstOrNull { it.id == id }?.let { notice ->
            InlineAlert("Update archived", notice.title, actions = {
                ActionButton({ onChange(values.undoArchive()) }, variant = ButtonVariant.SECONDARY, enabled = values.canUndo) { Text("Undo archive") }
                ActionButton({ onChange(values.copy(undoID = null)) }, variant = ButtonVariant.QUIET) { Text("Dismiss archive notice") }
            })
        } }
        if (values.visible.isEmpty()) Card { EmptyState(if (values.filter == InboxFilter.UNREAD) "You're all caught up" else "Your inbox is empty",
            "Switch filters or reset the inbox to explore more updates.") }
        listOf("Today", "Earlier").forEach { group ->
            val notices = values.visible.filter { it.group == group }
            if (notices.isNotEmpty()) {
                SectionHeader(group, subtitle = "${notices.size} ${inboxUpdateNoun(notices.size)}")
                notices.forEach { notice -> key(notice.id) { Card {
                    val unread = notice.id !in values.read
                    NotificationRow(notice.title, notice.message, notice.time, if (unread) "Unread" else "Read", unread,
                        "${notice.title}, ${notice.message}, ${notice.time}, ${if (unread) "Unread" else "Read"}",
                        onOpen = { val next = values.open(notice.id); onChange(next); showDetail = next.canShowDetail && next.opened == notice.id },
                        enabled = values.enabled, leading = { Text(notice.artwork, Modifier.size(40.dp), style = t.typography.heading, color = t.colors.accent.color) }, actions = {
                            ActionButton({ onChange(values.setRead(notice.id, unread)) }, variant = ButtonVariant.QUIET, enabled = values.enabled) {
                                Text("Mark ${notice.title} as ${if (unread) "read" else "unread"}")
                            }
                            ActionMenu("Actions for ${notice.title}", listOf(MenuAction("archive", "Archive ${notice.title}", enabled = values.enabled) { onChange(values.archive(notice.id)) }))
                        })
                } } }
            }
        }
    }
    SheetPanel("Update details", showDetail, { showDetail = false }, "Close update") {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            values.openedNotice?.let { notice ->
                SectionHeader(notice.title, subtitle = notice.time)
                Text(notice.message, style = t.typography.body)
                ActionButton({ onChange(values.archive(notice.id)) }, variant = ButtonVariant.SECONDARY, enabled = values.canShowDetail) { Text("Archive opened update") }
            }
        }
    }
}
