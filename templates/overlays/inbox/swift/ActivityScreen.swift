import FoundryServices
import FoundrySession
import SwiftUI

struct ActivityScreen: View {
    let model: ConnectedSessionModel
    var onOpen: ((InboxNotification) -> Void)? = nil
    var body: some View {
        let snapshot = model.controller.snapshot
        if snapshot.phase != .signedIn || model.controller.lease == nil {
            ContentUnavailableView("Your activity", systemImage: "tray", description: Text("Sign in from Account to view your inbox."))
        } else { InboxForm(store: model.inbox, onOpen: onOpen) }
    }
}

private struct InboxForm: View {
    @Bindable var store: InboxStore
    let onOpen: ((InboxNotification) -> Void)?
    var body: some View {
        Form {
            Section("Account inbox") {
                if let count = store.unreadCount {
                    Text("\(count) unread notifications").accessibilityIdentifier("inbox.count")
                    Text("Showing \(store.items.count) notifications.").accessibilityIdentifier("inbox.loaded")
                }
                Toggle("Unread only", isOn: Binding(get: { store.unreadOnly }, set: { store.chooseUnreadOnly($0) }))
                    .disabled(store.busy).accessibilityIdentifier("inbox.filter")
                Toggle("Media notifications", isOn: Binding(get: { store.mediaEnabled ?? false }, set: { store.setMediaEnabled($0) }))
                    .disabled(!store.canEdit).accessibilityIdentifier("inbox.media")
                Button("Refresh inbox") { store.refresh() }.disabled(store.busy).accessibilityIdentifier("inbox.refresh")
                if store.busy { ProgressView("Updating inbox…") }
                if store.needsRefresh {
                    Text("Refresh to confirm server state before another change.").font(.caption).accessibilityIdentifier("inbox.recovery")
                }
                if let failure = store.failure { Text(failure.meta.message).foregroundStyle(.red).accessibilityIdentifier("inbox.failure") }
                if store.nextCursor != nil {
                    Button("Load more notifications") { store.loadMore() }.disabled(!store.canLoadMore).accessibilityIdentifier("inbox.more")
                }
                if store.items.count >= 200 { Text("Showing up to 200 notices. Refresh for the newest items.").font(.caption) }
            }
            Section("Notifications") {
                if store.items.isEmpty, store.unreadCount != nil, !store.busy {
                    Text(store.unreadOnly ? "You're all caught up." : "Your account inbox is empty.").accessibilityIdentifier("inbox.empty")
                }
                ForEach(store.items) { item in
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Upload ready", systemImage: "arrow.up.doc")
                        Text("Your file finished uploading.")
                        Text(item.createdAt.formatted(date: .abbreviated, time: .shortened)).font(.caption)
                        Text(item.isUnread ? "Unread" : "Read").font(.caption).accessibilityIdentifier("inbox.state.\(item.id)")
                        if let onOpen {
                            Button("Open file") { onOpen(item) }.buttonStyle(.borderless).accessibilityIdentifier("inbox.open.\(item.id)")
                        }
                        if item.isUnread {
                            Button("Mark read") { store.markRead(item) }.buttonStyle(.borderless).disabled(!store.canEdit).accessibilityIdentifier("inbox.read.\(item.id)")
                        }
                    }.padding(.vertical, 4)
                }
            }
            Section {
                Text("Completed uploads appear after delivery. Media preferences affect future deliveries. Refresh to see new activity.").font(.caption)
            }
        }
    }
}
