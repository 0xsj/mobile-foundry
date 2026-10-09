import FoundryUI
import SwiftUI

enum InboxFilter: String, CaseIterable { case all = "All", unread = "Unread" }
/// English fixture copy; production localization belongs to the caller.
func inboxUpdateNoun(_ count: Int) -> String { count == 1 ? "update" : "updates" }
struct InboxNotice: Identifiable {
    let id: String; let title: String; let message: String; let time: String; let group: String; let symbol: String
    static let all = [Self(id: "review", title: "Review is ready", message: "Your latest studio study is ready for a closer look.", time: "2 minutes ago", group: "Today", symbol: "checkmark.bubble"),
                      Self(id: "invite", title: "Workspace invitation", message: "A new place to gather ideas and collaborate.", time: "1 hour ago", group: "Today", symbol: "person.crop.circle.badge.plus"),
                      Self(id: "export", title: "Export complete", message: "Your image has been prepared for sharing.", time: "Yesterday", group: "Earlier", symbol: "square.and.arrow.up"),
                      Self(id: "tools", title: "New editing tools", message: "Explore a few new ways to shape your next image.", time: "Monday", group: "Earlier", symbol: "slider.horizontal.3")]
}
/// Bounded local inbox. Read identity survives filters and archive; undo restores only the latest archived identity.
struct NotificationValues {
    var filter = InboxFilter.all
    var read = ["export"]; var archived: [String] = []
    var undoID: String?; var opened: String?; var opens = 0; var enabled = true
    var active: [InboxNotice] { InboxNotice.all.filter { !archived.contains($0.id) } }
    var visible: [InboxNotice] { active.filter { filter == .all || !read.contains($0.id) } }
    var unreadCount: Int { active.filter { !read.contains($0.id) }.count }
    var canMarkVisible: Bool { enabled && visible.contains { !read.contains($0.id) } }
    var canUndo: Bool { enabled && undoID.map { archived.contains($0) } == true }
    var openedNotice: InboxNotice? { InboxNotice.all.first { $0.id == opened } }
    var canShowDetail: Bool { enabled && active.contains { $0.id == opened } }
    mutating func chooseFilter(_ value: InboxFilter) { guard enabled else { return }; filter = value }
    mutating func setRead(_ id: String, _ value: Bool) {
        guard enabled && active.contains(where: { $0.id == id }) else { return }
        read.removeAll { $0 == id }; if value { read.append(id) }
    }
    mutating func markVisibleRead() {
        guard canMarkVisible else { return }
        let ids = visible.map(\.id)
        for id in ids { setRead(id, true) }
    }
    mutating func open(_ id: String) {
        guard enabled && active.contains(where: { $0.id == id }) else { return }
        setRead(id, true); opened = id; opens += 1
    }
    mutating func archive(_ id: String) {
        guard enabled && active.contains(where: { $0.id == id }) else { return }
        archived.append(id); undoID = id
    }
    mutating func undoArchive() {
        guard canUndo, let id = undoID else { return }
        archived.removeAll { $0 == id }; undoID = nil
    }
    mutating func reset() { guard enabled else { return }; filter = .all; read = ["export"]; archived = []; undoID = nil; opened = nil }
}
struct NotificationExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: NotificationValues
    var body: some View {
        Card {
            SectionHeader("An everyday inbox", subtitle: "Readable counts, independent row actions and grouped updates.")
            CountBadge("99+", accessibilityLabel: "128 unread updates")
            NavLink("Open inbox preview", subtitle: "Try read state, filters, archive and undo") {
                NotificationPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        NotificationContent(values: $values)
    }
}
struct NotificationPreview: View {
    @Binding var values: NotificationValues
    let appearance: FoundryAppearance; let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { NotificationContent(values: $values).padding(20) } }
            .navigationTitle("Inbox preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct NotificationContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: NotificationValues
    @State private var showDetail = false
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable inbox actions", isOn: $values.enabled)
                SectionHeader("Inbox", subtitle: "\(values.visible.count) visible \(inboxUpdateNoun(values.visible.count))") {
                    CountBadge("\(values.unreadCount)", accessibilityLabel: "\(values.unreadCount) unread \(inboxUpdateNoun(values.unreadCount)) in inbox")
                }
                SelectField("Inbox filter", selection: Binding(get: { values.filter }, set: { values.chooseFilter($0) }),
                     options: InboxFilter.allCases, enabled: values.enabled, label: { $0.rawValue })
                ActionButton("Mark visible as read", variant: .secondary, enabled: values.canMarkVisible) { values.markVisibleRead() }
                ActionButton("Reset inbox", variant: .quiet, enabled: values.enabled) { values.reset() }
                Text("Updates opened: \(values.opens)").font(t.typography.caption)
                Text("Last opened: \(values.openedNotice?.title ?? "None")").font(t.typography.caption)
            }
            if let id = values.undoID, let notice = InboxNotice.all.first(where: { $0.id == id }) {
                InlineAlert("Update archived", message: notice.title) {
                    ActionButton("Undo archive", variant: .secondary, enabled: values.canUndo) { values.undoArchive() }
                    ActionButton("Dismiss archive notice", variant: .quiet) { values.undoID = nil }
                }
            }
            if values.visible.isEmpty {
                Card { EmptyState(values.filter == .unread ? "You're all caught up" : "Your inbox is empty", message: "Switch filters or reset the inbox to explore more updates.") }
            }
            ForEach(["Today", "Earlier"], id: \.self) { group in
                let notices = values.visible.filter { $0.group == group }
                if !notices.isEmpty {
                    SectionHeader(group, subtitle: "\(notices.count) \(inboxUpdateNoun(notices.count))")
                    ForEach(notices) { notice in
                        let unread = !values.read.contains(notice.id)
                        Card {
                            NotificationRow(notice.title, message: notice.message, timeLabel: notice.time, stateLabel: unread ? "Unread" : "Read", isUnread: unread,
                                            accessibilityLabel: "\(notice.title), \(notice.message), \(notice.time), \(unread ? "Unread" : "Read")",
                                            enabled: values.enabled, onOpen: { values.open(notice.id); showDetail = values.canShowDetail && values.opened == notice.id }, leading: {
                                Image(systemName: notice.symbol).font(.title2).frame(width: 40, height: 40).foregroundStyle(t.colors.accent.color)
                            }, actions: {
                                ActionButton("Mark \(notice.title) as \(unread ? "read" : "unread")", variant: .quiet, enabled: values.enabled) { values.setRead(notice.id, unread) }
                                ActionMenu("Actions for \(notice.title)", actions: [MenuAction(id: "archive", title: "Archive \(notice.title)", enabled: values.enabled) { values.archive(notice.id) }])
                            })
                        }
                    }
                }
            }
        }
        .sheetPanel("Update details", isPresented: $showDetail, closeLabel: "Close update") {
            ScrollView {
                if let notice = values.openedNotice {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        SectionHeader(notice.title, subtitle: notice.time)
                        Text(notice.message).font(t.typography.body)
                        ActionButton("Archive opened update", variant: .secondary, enabled: values.canShowDetail) { values.archive(notice.id) }
                    }
                }
            }.presentationDetents([.medium, .large])
        }
        .onChange(of: values.canShowDetail) { _, available in if !available { showDetail = false } }
    }
}
