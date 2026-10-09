@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func accountScopedRemovalsRejectStaleContextAndPermissionStateIsDeviceScoped() {
    var values = AccountValues()
    values.select("invited"); values.select("unknown")
    #expect(values.accountID == "personal")
    values.removeSession("phone", expectedAccountID: "personal")
    values.removeSession("unknown", expectedAccountID: "personal")
    #expect(values.sessions.count == 3)
    values.removeSession("desktop", expectedAccountID: "personal")
    #expect(values.sessions.map(\.id) == ["phone", "tablet"])
    values.select("studio"); values.removeSession("tablet", expectedAccountID: "personal")
    #expect(values.sessions.count == 3 && values.removed == ["personal:desktop"])
    values.allowPhotos(); values.previewProfile(); values.select("personal")
    #expect(values.permission == .allowed && values.sessions.count == 2 && values.profilePreviews == 1)
    values.enabled = false
    values.select("studio"); values.removeSession("tablet", expectedAccountID: "personal"); values.previewProfile()
    values.setPermission(.denied); values.previewSettings()
    #expect(values.accountID == "personal" && values.sessions.count == 2 && values.permission == .allowed)
    #expect(values.profilePreviews == 1 && values.settingsPreviews == 0)
    values.enabled = true; values.setPermission(.denied); values.allowPhotos(); values.previewSettings()
    #expect(values.permission == .denied && values.settingsPreviews == 1)
    values.removeSession("tablet", expectedAccountID: "personal"); values.removeSession("tablet", expectedAccountID: "personal")
    #expect(values.sessions.map(\.id) == ["phone"] && values.removed.count == 2)
}
@Observable @MainActor private final class AccountProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct AccountFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func accountFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: AccountFrames.self, value: [id: proxy.frame(in: .named("account-probe"))]) } }
    }
}
private struct AccountProbe: View {
    @Bindable var values: AccountProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ProfileHeader("Mira Chen and the studio", detail: "A longer account description for the preview", avatar: {
                        Avatar("Decorative avatar", fallback: "MC", size: 64).accountFrame("avatar")
                    }, status: { Badge("Team member") }, actions: {
                        ActionButton("Preview profile", variant: .secondary) {}.accountFrame("profile-action")
                    }).accountFrame("profile")
                    SessionRow("Desktop workspace", activityLabel: "Last active yesterday", detail: "A longer browser description that wraps", icon: {
                        Image(systemName: "laptopcomputer")
                    }, status: { Badge("Other device") }, actions: {
                        ActionButton("Remove device", variant: .destructive) {}.accountFrame("session-action")
                    }).accountFrame("session")
                    PermissionCard("Photos", message: "Choose a photo for an editor preview.", icon: {
                        Image(systemName: "photo")
                    }, status: { Badge("Ask") }, actions: {
                        ActionButton("Try photo access") {}.accountFrame("permission-action")
                    }).accountFrame("permission")
                }
            }.frame(width: 240).coordinateSpace(name: "account-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(AccountFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func accountCompositionsGrowAtNarrowWidthAndKeepIndependentActionBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 1800)
    let values = AccountProbeValues()
    let host = UIHostingController(rootView: AccountProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Account geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 7 }
    let normal = try #require(values.frames["profile"])
    values.textSize = .accessibility3
    try await settle { (values.frames["profile"]?.height ?? 0) > normal.height + 40 }
    for name in ["profile", "session", "permission"] {
        let region = try #require(values.frames[name]), action = try #require(values.frames["\(name)-action"])
        #expect(region.width <= 241 && action.height >= 44 && action.width >= 44)
        #expect(action.minX >= region.minX - 1 && action.maxX <= region.maxX + 1)
    }
    let leadingAvatar = try #require(values.frames["avatar"])
    values.direction = .rightToLeft
    try await settle { (values.frames["avatar"]?.minX ?? 0) > leadingAvatar.minX + 100 }
}
