import FoundryUI
import SwiftUI
import UIKit

enum MemberRole: String, CaseIterable { case viewer = "Viewer", editor = "Editor" }
enum LinkAccess: String, CaseIterable {
    case off = "Off", view = "Can view", edit = "Can edit"
    var detail: String {
        switch self { case .off: "Only invited members have access in this preview."; case .view: "Anyone with the example link can view in this preview."; case .edit: "Anyone with the example link can edit in this preview." }
    }
}
struct SharingContact: Identifiable {
    let id: String; let name: String; let email: String; let initials: String
    var owner = false; var available = true
    static let all: [SharingContact] = [
        .init(id: "alex", name: "Alex Morgan", email: "alex@example.test", initials: "AM", owner: true),
        .init(id: "jamie", name: "Jamie Park", email: "jamie@example.test", initials: "JP"),
        .init(id: "sam", name: "Sam Chen", email: "sam@example.test", initials: "SC"),
        .init(id: "river", name: "River Vale", email: "river@example.test", initials: "RV"),
        .init(id: "lena", name: "Lena Hart", email: "lena@example.test", initials: "LH", available: false)
    ]
    static func find(_ id: String) -> SharingContact? { all.first { $0.id == id } }
}
struct MemberRemoval: Equatable { let id: String; let revision: Int }
/// Local fixture membership. A confirmation identifies the membership revision, not merely a visible row index.
struct SharingValues: Equatable {
    var members = ["alex", "jamie", "sam"]
    var roles: [String: MemberRole] = ["jamie": .editor, "sam": .viewer]
    var draft = ""
    var inviteRole = MemberRole.viewer
    var error: String?
    var linkAccess = LinkAccess.view
    var enabled = true
    var pending = false
    var revision = 0
    var invites = 0
    var roleChanges = 0
    var removals = 0
    var copies = 0
    static let link = "https://example.test/share/atlas"
    var canEdit: Bool { enabled && !pending }
    var canInvite: Bool { canEdit && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var canCopy: Bool { canEdit && linkAccess != .off }
    var contacts: [SharingContact] { members.compactMap(SharingContact.find) }
    func role(_ id: String) -> MemberRole { roles[id] ?? .viewer }
    func canManage(_ id: String) -> Bool { canEdit && members.contains(id) && SharingContact.find(id).map { !$0.owner && $0.available } == true }
    mutating func editDraft(_ value: String) { guard canEdit else { return }; draft = value; error = nil }
    mutating func chooseInviteRole(_ value: MemberRole) { guard canEdit else { return }; inviteRole = value }
    @discardableResult mutating func invite() -> Bool {
        guard canInvite else { return false }
        let address = draft.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard let contact = SharingContact.all.first(where: { $0.email == address }) else {
            error = "Use river@example.test in this preview."; return false
        }
        guard contact.available else { error = "This teammate is unavailable."; return false }
        guard !members.contains(contact.id) else { error = "Already a member."; return false }
        members.append(contact.id); roles[contact.id] = inviteRole; revision += 1; invites += 1; draft = ""; error = nil
        return true
    }
    mutating func changeRole(_ id: String, to value: MemberRole) {
        guard canManage(id) && role(id) != value else { return }; roles[id] = value; revision += 1; roleChanges += 1
    }
    func removal(_ id: String) -> MemberRemoval? { canManage(id) ? .init(id: id, revision: revision) : nil }
    func canRemove(_ request: MemberRemoval) -> Bool { request.revision == revision && canManage(request.id) }
    mutating func remove(_ request: MemberRemoval) {
        guard canRemove(request) else { return }; members.removeAll { $0 == request.id }; roles.removeValue(forKey: request.id); revision += 1; removals += 1
    }
    mutating func chooseLinkAccess(_ value: LinkAccess) { guard canEdit else { return }; linkAccess = value }
    mutating func copyLink() -> String? { guard canCopy else { return nil }; copies += 1; return Self.link }
    mutating func resetMembers() {
        guard canEdit else { return }; members = ["alex", "jamie", "sam"]; roles = ["jamie": .editor, "sam": .viewer]; error = nil; revision += 1
    }
}
struct SharingExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: SharingValues
    var body: some View {
        Card {
            SectionHeader("Sharing and access", subtitle: "People, role controls, guarded invitations and selectable links.")
            NavLink("Open sharing preview", subtitle: "Try local workspace members and link access") {
                SharingPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        SharingContent(values: $values)
    }
}
struct SharingPreview: View {
    @Binding var values: SharingValues
    let appearance: FoundryAppearance; let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { SharingContent(values: $values).padding(20) } }
            .navigationTitle("Sharing preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct SharingContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: SharingValues
    var copyText: (String) -> Void = { UIPasteboard.general.string = $0 }
    @FocusState private var inviteFocused: Bool
    @State private var removal: MemberRemoval?
    @State private var showRemoval = false
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable sharing actions", isOn: $values.enabled)
                ToggleField("Show pending access update", isOn: $values.pending, enabled: values.enabled)
                Text("Local sharing preview. Invite river@example.test to add River; no invitation is sent.").font(t.typography.caption)
                ActionButton("Reset members", variant: .quiet, enabled: values.canEdit) { values.resetMembers() }
                Text("Invites: \(values.invites) · Role changes: \(values.roleChanges) · Removals: \(values.removals)").font(t.typography.caption)
            }
            Card {
                SectionHeader("Invite a teammate", subtitle: "Choose the new member's access before adding them.")
                SelectField("Invite as", selection: Binding(get: { values.inviteRole }, set: { values.chooseInviteRole($0) }), options: MemberRole.allCases,
                            enabled: values.canEdit, label: { $0.rawValue })
                InlineActionField("Invite address", text: Binding(get: { values.draft }, set: { values.editDraft($0) }), actionLabel: "Add preview member",
                                  canSubmit: values.canInvite, enabled: values.enabled, isBusy: values.pending, error: values.error,
                                  focus: $inviteFocused) { if values.invite() { inviteFocused = false } }
                    .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                ActionButton("Use River address", variant: .quiet, enabled: values.canEdit) { values.editDraft("river@example.test") }
            }
            ShareLinkCard("Workspace link", link: values.linkAccess == .off ? nil : SharingValues.link, unavailableLabel: "Link access is off", detail: values.linkAccess.detail,
                          status: { Badge(values.linkAccess.rawValue, tone: values.linkAccess == .off ? .warning : .info) }, actions: {
                SelectField("Link access", selection: Binding(get: { values.linkAccess }, set: { values.chooseLinkAccess($0) }), options: LinkAccess.allCases,
                            enabled: values.canEdit, label: { $0.rawValue })
                ActionButton("Copy workspace link", variant: .secondary, enabled: values.canCopy) { if let link = values.copyLink() { copyText(link) } }
                Text("Copy requests: \(values.copies)").font(t.typography.caption)
            })
            Card {
                SectionHeader("Workspace members", subtitle: "\(values.members.count) \(values.members.count == 1 ? "member" : "members")")
                ForEach(values.contacts) { contact in
                    MemberRow(contact.name, detail: contact.email, accessibilityLabel: "\(contact.name), \(contact.email)", avatar: {
                        Avatar(contact.name, fallback: contact.initials, size: 48)
                    }, access: {
                        if contact.owner { Badge("Owner · Protected") }
                        else {
                            SelectField("Access for \(contact.name)", selection: Binding(get: { values.role(contact.id) }, set: { values.changeRole(contact.id, to: $0) }),
                                        options: MemberRole.allCases, enabled: values.canManage(contact.id), label: { $0.rawValue })
                        }
                    }, actions: {
                        ActionButton("Remove \(contact.name)", variant: .destructive, enabled: values.canManage(contact.id)) {
                            removal = values.removal(contact.id); inviteFocused = false; showRemoval = removal != nil
                        }
                    })
                    Divider()
                }
            }
        }
        .confirmationPrompt("Remove member?", message: "Remove \(removal.flatMap { SharingContact.find($0.id) }?.name ?? "this member") from the preview workspace?",
                            isPresented: $showRemoval, confirmLabel: "Remove preview member", cancelLabel: "Keep member", destructive: true) {
            if let removal { values.remove(removal) }; removal = nil
        }
        .onChange(of: values.canEdit) { _, canEdit in if !canEdit { dismissRemoval() } }
        .onChange(of: values.revision) { _, _ in dismissRemoval() }
    }
    private func dismissRemoval() { showRemoval = false; removal = nil }
}
