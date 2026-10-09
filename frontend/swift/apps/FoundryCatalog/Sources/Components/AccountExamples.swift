import FoundryUI
import SwiftUI

enum PermissionScenario: String, CaseIterable { case ask = "Ask", allowed = "Allowed", denied = "Denied" }
struct PreviewSession: Identifiable {
    let id: String; let title: String; let detail: String; let activity: String; let symbol: String; let current: Bool
    static let all = [Self(id: "phone", title: "iPhone 17", detail: "iOS · This device", activity: "Active now", symbol: "iphone", current: true),
                      Self(id: "desktop", title: "MacBook Pro", detail: "macOS · Desktop browser", activity: "Last active yesterday", symbol: "laptopcomputer", current: false),
                      Self(id: "tablet", title: "iPad Air", detail: "iPadOS · Tablet app", activity: "Last active October 5", symbol: "ipad", current: false)]
}
struct AccountValues {
    var accountID = "personal"
    var enabled = true
    var removed = Set<String>()
    var permission: PermissionScenario = .ask
    var profilePreviews = 0
    var settingsPreviews = 0
    static let options = [AccountOption(id: "personal", title: "Personal", detail: "Mira Chen"),
                          AccountOption(id: "studio", title: "Studio team", detail: "Shared workspace"),
                          AccountOption(id: "invited", title: "Invited workspace", detail: "Invitation pending", enabled: false)]
    var accountTitle: String { Self.options.first { $0.id == accountID }?.title ?? "Choose an account" }
    var sessions: [PreviewSession] { PreviewSession.all.filter { !removed.contains("\(accountID):\($0.id)") } }
    mutating func select(_ id: String) {
        guard enabled && Self.options.contains(where: { $0.id == id && $0.enabled }) else { return }; accountID = id
    }
    mutating func removeSession(_ id: String, expectedAccountID: String) {
        guard enabled && accountID == expectedAccountID && sessions.contains(where: { $0.id == id && !$0.current }) else { return }
        removed.insert("\(accountID):\(id)")
    }
    mutating func previewProfile() { guard enabled else { return }; profilePreviews += 1 }
    mutating func allowPhotos() { guard enabled && permission == .ask else { return }; permission = .allowed }
    mutating func previewSettings() { guard enabled && permission == .denied else { return }; settingsPreviews += 1 }
    mutating func setPermission(_ value: PermissionScenario) { guard enabled else { return }; permission = value }
}
struct AccountExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: AccountValues
    var body: some View {
        Card {
            SectionHeader("Identity and access", subtitle: "Profiles, account choices, devices and clear permission rationale.")
            NavLink("Open account center", subtitle: "Explore a local account and access preview") {
                AccountPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        AccountContent(values: $values)
    }
}
struct AccountPreview: View {
    @Binding var values: AccountValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { AccountContent(values: $values).padding(20) } }
            .navigationTitle("Account center").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
private struct SessionRemoval { let accountID: String; let sessionID: String; let title: String }
struct AccountContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: AccountValues
    @State private var removal: SessionRemoval?
    @State private var showRemoval = false
    @State private var showPermission = false
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable account actions", isOn: $values.enabled)
                Text("Local preview. Device removal and photo access only change this example.").font(t.typography.caption)
                AccountSwitcher("Switch account", options: AccountValues.options, selection: values.accountID,
                                placeholder: "Choose an account", enabled: values.enabled) { values.select($0) }
                Text("Active account: \(values.accountTitle)").font(t.typography.caption)
            }
            Card {
                ProfileHeader("Mira Chen", detail: "mira@example.test", avatar: {
                    Avatar("Mira Chen", fallback: "MC", size: 64)
                }, status: { Badge(values.accountID == "studio" ? "Team member" : "Personal account", tone: .info) }, actions: {
                    ActionButton("Preview profile", variant: .secondary, enabled: values.enabled) { values.previewProfile() }
                })
                Text("Profile previews: \(values.profilePreviews)").font(t.typography.caption)
            }
            PermissionCard("Photos", message: "Choose a photo for an editor preview. Photo access is shared by these preview accounts.",
                           icon: { Image(systemName: "photo.on.rectangle").font(.title2) }, status: {
                Badge("Preview: \(values.permission.rawValue)", tone: values.permission == .denied ? .warning : .info)
            }, actions: {
                switch values.permission {
                case .ask: ActionButton("Try photo access", enabled: values.enabled) { showPermission = true }
                case .denied: ActionButton("Preview settings", variant: .secondary, enabled: values.enabled) { values.previewSettings() }
                case .allowed: Text("The preview can use photos.").font(t.typography.caption)
                }
            })
            Card {
                SelectField("Photo access scenario", selection: Binding(get: { values.permission }, set: { values.setPermission($0) }),
                            options: PermissionScenario.allCases, enabled: values.enabled, label: { $0.rawValue })
                Text("Settings previews: \(values.settingsPreviews)").font(t.typography.caption)
            }
            Card {
                SectionHeader("Devices", subtitle: "Only other devices can be removed in this preview.")
                Text("Devices: \(values.sessions.count)").font(t.typography.caption)
                ForEach(values.sessions) { session in
                    SessionRow(session.title, activityLabel: session.activity, detail: session.detail,
                               icon: { Image(systemName: session.symbol).font(.title2) }, status: {
                        Badge(session.current ? "Current device" : "Other device")
                    }, actions: {
                        ActionButton("Remove device", variant: .destructive, enabled: values.enabled && !session.current) {
                            removal = SessionRemoval(accountID: values.accountID, sessionID: session.id, title: session.title); showRemoval = true
                        }.accessibilityLabel("Remove \(session.title)")
                    })
                }
            }
        }
        .confirmationPrompt("Remove device?", message: "Remove \(removal?.title ?? "this device") from this account preview?",
                            isPresented: $showRemoval, confirmLabel: "Remove preview device", cancelLabel: "Keep device", destructive: true) {
            if let removal { values.removeSession(removal.sessionID, expectedAccountID: removal.accountID) }; removal = nil
        }
        .confirmationPrompt("Allow photo preview?", message: "This changes the local preview only; it does not request system photo access.",
                            isPresented: $showPermission, confirmLabel: "Allow preview", cancelLabel: "Not now") { values.allowPhotos() }
        .onChange(of: values.accountID) { _, _ in dismissPrompts() }
        .onChange(of: values.enabled) { _, enabled in if !enabled { dismissPrompts() } }
        .onChange(of: values.permission) { _, _ in showPermission = false }
    }
    private func dismissPrompts() { showRemoval = false; showPermission = false; removal = nil }
}
