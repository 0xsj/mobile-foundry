import FoundryUI
import SwiftUI

struct BrowserItem: Identifiable {
    let id: String
    let parent: String?
    let title: String
    let format: String
    let detail: String
    var folder = false
    var available = true
    static let fixtures: [BrowserItem] = [
        .init(id: "atlas", parent: nil, title: "Atlas", format: "DIR", detail: "2 items", folder: true),
        .init(id: "studies", parent: "atlas", title: "Studies", format: "DIR", detail: "2 items", folder: true),
        .init(id: "refs", parent: "studies", title: "References", format: "DIR", detail: "1 item", folder: true),
        .init(id: "field", parent: "refs", title: "Field image.jpg", format: "JPEG", detail: "JPEG · 860 KB"),
        .init(id: "lens", parent: "studies", title: "Lens study.png", format: "PNG", detail: "PNG · 1.2 MB"),
        .init(id: "brief", parent: "atlas", title: "Brief.md", format: "MD", detail: "Markdown · 4 KB"),
        .init(id: "notes", parent: nil, title: "Field notes.md", format: "MD", detail: "Markdown · 2 KB"),
        .init(id: "locked", parent: nil, title: "Restricted.txt", format: "TXT", detail: "Unavailable in this preview", available: false)
    ]
    static func find(_ id: String) -> BrowserItem? { fixtures.first { $0.id == id } }
    var path: String { parent.flatMap(Self.find).map { "\($0.path) / \(title)" } ?? title }
}
struct BrowserEntry: Identifiable { let item: BrowserItem; let depth: Int; var id: String { item.id } }

/// Projection over the bounded, acyclic fixture tree. Visibility never rewrites saved expansion or selected identity.
struct FileBrowserValues: Equatable {
    var query = ""
    var expanded = ["atlas", "studies"]
    var favorites: [String] = []
    var selected: String?
    var opens = 0
    var enabled = true
    var empty = false
    var searching: Bool { !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var canEdit: Bool { enabled && !empty }
    private var searchIDs: Set<String> {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        var included: Set<String> = []
        for item in BrowserItem.fixtures where item.title.localizedCaseInsensitiveContains(term) {
            var next: BrowserItem? = item
            while let current = next { included.insert(current.id); next = current.parent.flatMap(BrowserItem.find) }
        }
        return included
    }
    var entries: [BrowserEntry] {
        guard !empty else { return [] }
        let included = searching ? searchIDs : nil
        var result: [BrowserEntry] = []
        func visit(_ parent: String?, depth: Int) {
            for item in BrowserItem.fixtures where item.parent == parent {
                guard included?.contains(item.id) ?? true else { continue }
                result.append(.init(item: item, depth: depth))
                if item.folder && (searching || expanded.contains(item.id)) { visit(item.id, depth: depth + 1) }
            }
        }
        visit(nil, depth: 0)
        return result
    }
    func isExpanded(_ id: String) -> Bool {
        searching ? BrowserItem.fixtures.contains { $0.parent == id && searchIDs.contains($0.id) } : expanded.contains(id)
    }
    func canOpen(_ id: String) -> Bool { canEdit && BrowserItem.find(id)?.available == true && entries.contains { $0.id == id } }
    var canInspect: Bool { canEdit && selected.flatMap(BrowserItem.find)?.available == true }
    mutating func search(_ text: String) { guard enabled else { return }; query = text }
    mutating func toggle(_ id: String) {
        guard canEdit && !searching && BrowserItem.find(id)?.folder == true && entries.contains(where: { $0.id == id }) else { return }
        if expanded.contains(id) { expanded.removeAll { $0 == id } } else { expanded.append(id) }
    }
    mutating func open(_ id: String) { guard canOpen(id) else { return }; selected = id; opens += 1 }
    mutating func favorite(_ id: String) {
        guard canEdit && BrowserItem.find(id)?.available == true else { return }
        if favorites.contains(id) { favorites.removeAll { $0 == id } } else { favorites.append(id) }
    }
    mutating func collapseAll() { guard canEdit && !searching else { return }; expanded = [] }
    mutating func resetBrowser() { guard enabled else { return }; query = ""; expanded = ["atlas", "studies"]; selected = nil; empty = false }
}

struct FileExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: FileBrowserValues
    var body: some View {
        Card {
            SectionHeader("Files and hierarchy", subtitle: "Open rows, disclose branches and keep related actions independent.")
            NavLink("Open files preview", subtitle: "Explore folders, search, favorites and an inspector") {
                FilePreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        FileContent(values: $values)
    }
}
struct FilePreview: View {
    @Binding var values: FileBrowserValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { FileContent(values: $values).padding(20) } }
            .navigationTitle("Files preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct FileContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: FileBrowserValues
    @State private var showInspector = false
    @FocusState private var searchFocused: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable file actions", isOn: $values.enabled)
                ToggleField("Show empty browser", isOn: $values.empty, enabled: values.enabled)
                SearchField("Search files", text: Binding(get: { values.query }, set: { values.search($0) }), clearLabel: "Clear file search", enabled: values.enabled)
                    .focused($searchFocused).onSubmit { searchFocused = false }
                Text("Local fixture files. Search reveals matching paths; clearing it restores your folder choices.").font(t.typography.caption)
                ActionButton("Collapse all folders", variant: .secondary, enabled: values.canEdit && !values.searching) { values.collapseAll() }
                ActionButton("Reset browser", variant: .quiet, enabled: values.enabled) { values.resetBrowser() }
                Text("File opens: \(values.opens) · Favorites: \(values.favorites.count)").font(t.typography.caption)
                Text("Last opened: \(values.selected.flatMap(BrowserItem.find)?.title ?? "None")").font(t.typography.caption)
            }
            Card {
                SectionHeader("Workspace files", subtitle: "\(values.entries.count) visible \(values.entries.count == 1 ? "item" : "items")")
                if values.entries.isEmpty {
                    EmptyState(values.empty ? "No files yet" : "No matching files", message: values.empty ? "Turn off the empty state to restore this preview." : "Try another name or clear your search.",
                               artwork: { Image(systemName: "folder").font(.largeTitle) }, actions: { EmptyView() })
                }
                ForEach(values.entries) { entry in
                    let item = entry.item
                    TreeRow(item.title, subtitle: "\(item.detail) · Level \(entry.depth + 1)",
                            accessibilityLabel: "\(item.available ? "Open" : "Unavailable") \(item.title), \(item.folder ? "Folder, " : "")\(item.detail), Level \(entry.depth + 1)",
                            depth: entry.depth, selected: values.selected == item.id, enabled: values.canOpen(item.id),
                            disclosure: item.folder ? TreeDisclosure(expanded: values.isExpanded(item.id),
                                actionLabel: "\(values.isExpanded(item.id) ? "Collapse" : "Expand") \(item.title)",
                                stateLabel: values.searching ? (values.isExpanded(item.id) ? "Expanded for search" : "No matching children") : values.isExpanded(item.id) ? "Expanded" : "Collapsed",
                                enabled: values.canEdit && !values.searching, onToggle: { values.toggle(item.id) }) : nil,
                            onOpen: { guard values.canOpen(item.id) else { return }; values.open(item.id); searchFocused = false; showInspector = values.canInspect }, leading: {
                        FileTypeMark(item.format, accessibilityLabel: item.folder ? "Folder" : item.format)
                    }, actions: {
                        IconAction("\(values.favorites.contains(item.id) ? "Unfavorite" : "Favorite") \(item.title)", enabled: values.canEdit && item.available,
                                   action: { values.favorite(item.id) }) {
                            Image(systemName: values.favorites.contains(item.id) ? "star.fill" : "star")
                        }.accessibilityAddTraits(values.favorites.contains(item.id) ? .isSelected : [])
                    })
                    Divider()
                }
            }
        }
        .sheetPanel("File inspector", isPresented: $showInspector, closeLabel: "Close file inspector") {
            ScrollView {
                if let item = values.selected.flatMap(BrowserItem.find) {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        FileTypeMark(item.format, accessibilityLabel: item.folder ? "Folder" : item.format)
                        SectionHeader(item.title, subtitle: "Preview metadata")
                        KeyValueRow("Path", value: item.path)
                        KeyValueRow("Type", value: item.folder ? "Folder" : item.format)
                        KeyValueRow("Details", value: item.detail)
                        ActionButton(values.favorites.contains(item.id) ? "Remove inspector favorite" : "Favorite from inspector", enabled: values.canInspect) { values.favorite(item.id) }
                    }
                }
            }.presentationDetents([.medium, .large])
        }
        .onChange(of: values.canInspect) { _, canInspect in if !canInspect { showInspector = false } }
    }
}
