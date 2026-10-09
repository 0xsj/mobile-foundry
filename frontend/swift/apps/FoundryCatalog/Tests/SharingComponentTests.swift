@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func membershipAdmissionProtectsOwnersAndRechecksConfirmedRevision() throws {
    var values = SharingValues()
    #expect(values.contacts.count == 3 && values.removal("alex") == nil && values.removal("missing") == nil)
    values.changeRole("alex", to: .editor); values.changeRole("missing", to: .editor)
    #expect(values.revision == 0 && values.roleChanges == 0)
    values.editDraft("unknown@example.test"); #expect(!values.invite() && values.error != nil && values.invites == 0)
    values.editDraft("lena@example.test"); #expect(!values.invite() && values.error == "This teammate is unavailable.")
    values.editDraft(" RIVER@EXAMPLE.TEST \n"); values.chooseInviteRole(.editor)
    #expect(values.invite() && values.members == ["alex", "jamie", "sam", "river"] && values.role("river") == .editor)
    #expect(values.draft.isEmpty && values.error == nil && values.invites == 1)
    values.editDraft("river@example.test"); #expect(!values.invite() && values.error == "Already a member.")
    let stale = try #require(values.removal("jamie"))
    values.changeRole("jamie", to: .viewer); values.remove(stale)
    #expect(values.members.contains("jamie") && values.roleChanges == 1 && values.removals == 0)
    values.changeRole("jamie", to: .viewer); #expect(values.roleChanges == 1)
    let fresh = try #require(values.removal("jamie"))
    values.pending = true; let pending = values
    values.editDraft(""); values.chooseInviteRole(.viewer); values.invite(); values.changeRole("sam", to: .editor)
    values.remove(fresh); values.chooseLinkAccess(.off); values.resetMembers()
    #expect(values.copyLink() == nil && values == pending)
    values.pending = false; values.remove(fresh); values.remove(fresh)
    #expect(!values.members.contains("jamie") && values.removals == 1)
    #expect(values.copyLink() == SharingValues.link && values.copies == 1)
    values.chooseLinkAccess(.off); #expect(values.copyLink() == nil && values.copies == 1)
    values.enabled = false; let disabled = values
    values.editDraft(""); values.chooseInviteRole(.viewer); values.invite(); values.changeRole("sam", to: .editor)
    values.remove(.init(id: "sam", revision: values.revision)); values.chooseLinkAccess(.edit); values.resetMembers()
    #expect(values.copyLink() == nil && values == disabled)
    values.enabled = true; let beforeReset = try #require(values.removal("sam")); values.resetMembers(); values.remove(beforeReset)
    #expect(values.members == ["alex", "jamie", "sam"] && values.role("jamie") == .editor && values.error == nil)
    #expect(values.invites == 1 && values.removals == 1 && values.copies == 1 && values.linkAccess == .off)
}
@Observable @MainActor private final class SharingProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct SharingFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func sharingFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: SharingFrames.self, value: [id: proxy.frame(in: .named("sharing-probe"))]) } }
    }
}
private struct SharingProbe: View {
    @Bindable var values: SharingProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    MemberRow("A teammate with a detailed display name", detail: "teammate@example.test", accessibilityLabel: "Supplied teammate identity", avatar: {
                        Image(systemName: "person").frame(width: 24, height: 24).sharingFrame("mark")
                    }, access: { ActionButton("Change member access", variant: .secondary) {}.sharingFrame("role") },
                              actions: { ActionButton("Remove teammate", variant: .destructive) {}.sharingFrame("remove") }).sharingFrame("member")
                    ShareLinkCard("Workspace link", link: SharingValues.link, unavailableLabel: "Link access is off",
                                  detail: "Anyone with this supplied link can view", status: { Badge("Can view") }, actions: {
                        ActionButton("Copy workspace link", variant: .secondary) {}.sharingFrame("copy")
                    }).sharingFrame("link")
                }
            }.frame(width: 240).coordinateSpace(name: "sharing-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(SharingFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func memberIdentityAndSelectableLinkGrowWithinNarrowRTLBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 3000)
    let values = SharingProbeValues(); let host = UIHostingController(rootView: SharingProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Sharing geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 6 }
    let normalMember = try #require(values.frames["member"]), normalLink = try #require(values.frames["link"])
    values.textSize = .accessibility3
    try await settle { (values.frames["member"]?.height ?? 0) > normalMember.height + 60 && (values.frames["link"]?.height ?? 0) > normalLink.height + 60 }
    for id in ["member", "link"] { #expect((values.frames[id]?.width ?? 1000) <= 241) }
    for id in ["role", "remove", "copy"] {
        let frame = try #require(values.frames[id]); #expect(frame.height >= 44 && frame.width >= 44 && frame.minX >= normalMember.minX - 1 && frame.maxX <= normalMember.maxX + 1)
    }
    let mark = try #require(values.frames["mark"]); values.direction = .rightToLeft
    try await settle { (values.frames["mark"]?.minX ?? 0) > mark.minX + 150 }
}
