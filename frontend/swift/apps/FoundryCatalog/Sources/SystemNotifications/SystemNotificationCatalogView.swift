import FoundryServices
import FoundryUI
import OSLog
import SwiftUI

struct SystemNotificationCatalogView: View {
    @State private var model = SystemNotificationController(port: AppleSystemNotifications(), onUnexpectedError: { _ in
        Logger(subsystem: "dev.mobilefoundry.catalog", category: "system-notifications").error("Unexpected system notification failure.")
    })
    @Environment(\.foundry) private var t
    @Environment(\.scenePhase) private var phase
    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                Card {
                    SectionHeader("Notification permission", subtitle: "Device permission is separate from your account's in-app inbox preference.")
                    Text("Permission: \(model.access?.authorization.rawValue ?? "Unknown")").accessibilityIdentifier("notifications.permission")
                    Text(model.access?.deliveryEnabled == true ? "Delivery settings: enabled" : "Delivery settings: disabled or unread")
                    ActionButton("Allow notifications", enabled: !model.busy) { model.requestPermission() }.accessibilityIdentifier("notifications.allow")
                    ActionButton("Refresh permission", variant: .secondary, enabled: !model.busy) { model.refresh() }
                    ActionButton("Open notification settings", variant: .quiet, enabled: !model.busy) { model.openSettings() }
                    Text("If permission was denied, use Settings. Focus, quiet delivery and system policy can affect presentation.").font(t.typography.caption)
                }
                Card {
                    SectionHeader("Local preview", subtitle: "A generic device notification, with no backend or account content.")
                    ActionButton("Show test notification in 3 seconds", enabled: model.canPost) { model.postPreview() }.accessibilityIdentifier("notifications.post")
                    ActionButton("Clear test notification", variant: .secondary, enabled: !model.busy) { model.clearPreview() }.accessibilityIdentifier("notifications.clear")
                    if let id = model.lastAcceptedID { Text("Accepted by OS: \(id)").font(t.typography.caption).accessibilityIdentifier("notifications.accepted") }
                    Text("Tap the notification to return here. You can leave the app before it appears. OS acceptance does not prove display or remote push delivery.").font(t.typography.caption)
                }
                Card {
                    SectionHeader("Notification open")
                    Text(SystemNotificationComposition.opens.lastOpened.map { ($0.isRemote ? "Opened push preview: " : "Opened preview: ") + $0.previewID } ?? "No preview opened in this process").accessibilityIdentifier("notifications.opened")
                    Text("Preview taps only open this screen. Account selection, inbox reads and installation registration stay separate.").font(t.typography.caption)
                }
                Card {
                    SectionHeader("Remote preview", subtitle: "APNs / FCM notification opens return here without selecting an account.")
                    Text(SystemNotificationComposition.opens.lastReceived.map { "Received push preview: \($0.id)" } ?? "No remote preview received in this foreground process").accessibilityIdentifier("notifications.remote-received")
                    Text("Send a preview from your configured backend to test delivery. Receiving a foreground preview does not navigate; tap its notification to open this screen. Device permission and provider setup are required.").font(t.typography.caption)
                }
                if let failure = model.failure { Text(failure.meta.message).font(t.typography.caption) }
                PushTokenCatalogCard()
            }.padding(t.space.section)
        }.navigationTitle("System notifications").navigationBarTitleDisplayMode(.inline)
            .task { model.refresh() }
            .onChange(of: phase) { _, next in if next == .active { model.refresh() } }
            .onDisappear { model.close() }
    }
}
