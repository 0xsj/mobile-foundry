import FoundryServices
import FoundryUI
import SwiftUI

struct NotificationInboxCatalogCard: View {
    let model: NotificationInboxCatalogModel
    @State private var selected: InboxNotification?
    @Environment(\.foundry) private var t
    var body: some View {
        Card {
            SectionHeader("Account inbox", subtitle: "Upload a file, then refresh to see its upload-ready notice.") {
                if let count = model.unreadCount { CountBadge("\(count)", accessibilityLabel: "\(count) unread account notifications").accessibilityIdentifier("go.inbox.count") }
            }
            ToggleField("Unread only", isOn: Binding(get: { model.unreadOnly }, set: { model.chooseUnreadOnly($0) }), enabled: !model.busy)
            ToggleField("Media notifications", isOn: Binding(get: { model.mediaEnabled ?? false }, set: { model.setMediaEnabled($0) }), enabled: model.canEdit)
                .accessibilityIdentifier("go.inbox.media")
            ActionButton("Refresh inbox", variant: .secondary, enabled: !model.busy) { model.refresh() }.accessibilityIdentifier("go.inbox.refresh")
            if model.busy { ProgressView("Loading inbox…") }
            if model.needsRefresh { Text("Refresh to confirm server state before another change.").font(t.typography.caption) }
            if let failure = model.failure { Text(failure.meta.message).font(t.typography.caption).accessibilityIdentifier("go.inbox.failure") }
            if model.items.isEmpty && model.unreadCount != nil && !model.busy {
                EmptyState(model.unreadOnly ? "You're all caught up" : "Your account inbox is empty", message: "Completed uploads appear here after delivery. Media preferences affect future deliveries.")
            }
            ForEach(model.items) { item in
                NotificationRow("Upload ready", message: "Your file finished uploading.", timeLabel: item.createdAt.formatted(date: .abbreviated, time: .shortened),
                    stateLabel: item.isUnread ? "Unread" : "Read", isUnread: item.isUnread,
                    accessibilityLabel: "Upload ready, \(item.isUnread ? "Unread" : "Read")", enabled: !model.busy,
                    onOpen: { selected = item; model.markRead(item) }, leading: { Image(systemName: "arrow.up.doc").foregroundStyle(t.colors.accent.color) }, actions: {
                        if item.isUnread { ActionButton("Mark read", variant: .quiet, enabled: model.canEdit) { model.markRead(item) }.accessibilityIdentifier("go.inbox.read.\(item.id)") }
                    })
            }
            if model.nextCursor != nil {
                ActionButton("Load more notifications", variant: .quiet, enabled: model.canLoadMore) { model.loadMore() }
            }
            if model.items.count >= 200 { Text("Showing up to 200 notices. Refresh for the newest items.").font(t.typography.caption) }
        }
        .sheetPanel("Upload ready", isPresented: Binding(get: { selected != nil }, set: { if !$0 { selected = nil } }), closeLabel: "Close notification") {
            if let item = selected {
                Text("Your upload completed.")
                Text("File ID: \(item.fileID)").font(t.typography.code).textSelection(.enabled)
                Text("The file may have changed or been deleted since this notice arrived.").font(t.typography.caption)
            }
        }
    }
}
