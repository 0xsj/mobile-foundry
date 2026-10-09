@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func inboxCommandsKeepReadArchiveAndVisibleScopeIndependent() {
    var values = NotificationValues()
    #expect(values.unreadCount == 3 && values.visible.count == 4)
    values.setRead("missing", true); values.archive("missing"); values.open("missing")
    #expect(values.read == ["export"] && values.archived.isEmpty && values.opens == 0)
    values.archive("review"); values.archive("invite"); values.archive("invite")
    #expect(values.archived == ["review", "invite"] && values.undoID == "invite")
    values.chooseFilter(.unread); values.markVisibleRead()
    #expect(values.visible.isEmpty && values.read.contains("tools") && !values.read.contains("review") && !values.read.contains("invite"))
    values.undoArchive()
    #expect(values.visible.map(\.id) == ["invite"] && values.archived == ["review"] && values.undoID == nil)
    values.open("invite")
    #expect(values.visible.isEmpty && values.canShowDetail && values.opened == "invite" && values.opens == 1)
    values.chooseFilter(.all); values.setRead("invite", false)
    #expect(values.opened == "invite" && values.unreadCount == 1)
    values.enabled = false
    values.markVisibleRead(); values.setRead("invite", true); values.archive("invite"); values.open("export"); values.chooseFilter(.unread); values.reset()
    #expect(!values.read.contains("invite") && values.archived == ["review"] && values.opened == "invite" && values.opens == 1 && values.filter == .all)
    values.enabled = true; values.archive("invite")
    #expect(!values.canShowDetail && values.undoID == "invite")
    values.enabled = false; values.undoArchive(); #expect(values.archived.contains("invite"))
    values.enabled = true; values.undoArchive(); values.open("export")
    #expect(!values.read.contains("invite") && values.opened == "export" && values.opens == 2)
    values.reset(); values.markVisibleRead(); #expect(values.unreadCount == 0 && values.read.count == 4)
    values.markVisibleRead(); #expect(values.read.count == 4 && values.opens == 2)
}
@Observable @MainActor private final class InboxProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct InboxFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func inboxFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: InboxFrames.self, value: [id: proxy.frame(in: .named("inbox-probe"))]) } }
    }
}
private struct InboxProbe: View {
    @Bindable var values: InboxProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CountBadge("99+", accessibilityLabel: "128 unread updates").inboxFrame("count")
                    NotificationRow("A new review is ready for your attention", message: "Readable update copy wraps beside passive artwork and above independent actions.",
                                    timeLabel: "A few minutes ago", stateLabel: "Unread", isUnread: true,
                                    accessibilityLabel: "Supplied update, a few minutes ago, unread", onOpen: {}, leading: {
                        Image(systemName: "bell").frame(width: 40, height: 40).inboxFrame("artwork")
                    }, actions: { ActionButton("Mark this update as read") {}.inboxFrame("read")
                        ActionButton("Archive this update", variant: .secondary) {}.inboxFrame("archive") }).inboxFrame("row")
                }
            }.frame(width: 240).coordinateSpace(name: "inbox-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(InboxFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func notificationRowsGrowAndKeepIndependentActionsWithinNarrowLogicalBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 2200)
    let values = InboxProbeValues()
    let host = UIHostingController(rootView: InboxProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Inbox geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 5 }
    let normal = try #require(values.frames["row"])
    values.textSize = .accessibility3
    try await settle { (values.frames["row"]?.height ?? 0) > normal.height + 80 }
    let row = try #require(values.frames["row"])
    #expect(row.width <= 241)
    for id in ["read", "archive"] {
        let action = try #require(values.frames[id])
        #expect(action.height >= 44 && action.width >= 44 && action.minX >= row.minX - 1 && action.maxX <= row.maxX + 1)
    }
    let artwork = try #require(values.frames["artwork"])
    values.direction = .rightToLeft
    try await settle { (values.frames["artwork"]?.minX ?? 0) > artwork.minX + 150 }
    #expect((values.frames["count"]?.width ?? 0) <= 241)
}
