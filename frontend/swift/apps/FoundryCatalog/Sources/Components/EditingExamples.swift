import FoundryUI
import SwiftUI

struct LibraryPreviewItem: Identifiable {
    let id: String
    let title: String
    static let all = [LibraryPreviewItem(id: "orbit", title: "Orbit study"), .init(id: "field", title: "Field notes"),
                      .init(id: "atlas", title: "Atlas workspace"), .init(id: "material", title: "Material references"),
                      .init(id: "motion", title: "Motion sketches"), .init(id: "light", title: "Light experiments")]
}
struct EditingValues {
    var tagDraft = ""
    var tags = ["Sketch", "Review"]
    var search = ""
    var selected: Set<String> = []
    var archived: Set<String> = []
    var removed: Set<String> = []
    var undoIDs: Set<String> = []
    var undoSelection: Set<String> = []
    var enabled = true
    var visible: [LibraryPreviewItem] { LibraryPreviewItem.all.filter { !removed.contains($0.id) && (search.isEmpty || $0.title.localizedCaseInsensitiveContains(search)) } }
    var hiddenSelected: Int { selected.subtracting(Set(visible.map(\.id))).count }
    var canAdd: Bool { enabled && tags.count < 6 && !cleanTag.isEmpty && !duplicate }
    private var cleanTag: String { tagDraft.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var duplicate: Bool { tags.contains { $0.caseInsensitiveCompare(cleanTag) == .orderedSame } }
    var tagError: String? { tags.count >= 6 ? "Six tags already added." : duplicate ? "This tag is already present." : nil }
    mutating func addTag() { guard canAdd else { return }; tags.append(cleanTag); tagDraft = "" }
    mutating func removeTag(_ tag: String) { guard enabled else { return }; tags.removeAll { $0 == tag } }
    mutating func toggle(_ id: String) {
        guard enabled, LibraryPreviewItem.all.contains(where: { $0.id == id }), !removed.contains(id) else { return }
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }
    mutating func selectVisible() {
        guard enabled, !visible.isEmpty else { return }
        let ids = Set(visible.map(\.id))
        if ids.isSubset(of: selected) { selected.subtract(ids) } else { selected.formUnion(ids) }
    }
    mutating func archive(_ ids: Set<String>) {
        guard enabled else { return }
        archived.formUnion(ids.intersection(Set(LibraryPreviewItem.all.map(\.id))).subtracting(removed))
    }
    mutating func toggleArchive(_ id: String) {
        guard enabled, !removed.contains(id), LibraryPreviewItem.all.contains(where: { $0.id == id }) else { return }
        if archived.contains(id) { archived.remove(id) } else { archived.insert(id) }
    }
    mutating func remove(_ ids: Set<String>) {
        guard enabled else { return }
        let admitted = ids.intersection(Set(LibraryPreviewItem.all.map(\.id))).subtracting(removed)
        guard !admitted.isEmpty else { return }
        undoIDs = admitted; undoSelection = selected.intersection(admitted)
        removed.formUnion(admitted); selected.subtract(admitted)
    }
    mutating func undo() {
        guard enabled, !undoIDs.isEmpty else { return }
        removed.subtract(undoIDs); selected.formUnion(undoSelection)
        undoIDs = []; undoSelection = []
    }
}

struct EditingExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: EditingValues
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            ToggleField("Enable editing controls", isOn: $values.enabled)
            Card { LibraryTags(values: $values) }
            Card {
                SelectionRow("Orbit study", subtitle: values.archived.contains("orbit") ? "Archived" : "Local study",
                    selected: values.selected.contains("orbit"), stateDescription: values.selected.contains("orbit") ? "Selected" : "Not selected",
                    enabled: values.enabled && !values.removed.contains("orbit"), onToggle: { values.toggle("orbit") })
                Text("Selected items: \(values.selected.count)")
                NavLink("Open library editor", subtitle: "Selection, filtering, swipe actions and undo") {
                    LibraryEditingPreview(values: $values, appearance: t.appearance, style: t.materials.style)
                }
            }
        }
    }
}
private struct LibraryTags: View {
    @Binding var values: EditingValues
    @FocusState private var focused: Bool
    var body: some View {
        TokenField("New library tag", text: $values.tagDraft, addLabel: "Add library tag", canAdd: values.canAdd,
                   enabled: values.enabled, help: "One tag at a time. Up to six.", error: values.tagError, focus: $focused,
                   onAdd: { values.addTag() }) {
            ForEach(values.tags, id: \.self) { tag in
                RemovableChip(tag, removeLabel: "Remove tag \(tag)", onRemove: { values.removeTag(tag) })
            }
        }
    }
}
struct LibraryEditingPreview: View {
    @Binding var values: EditingValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { LibraryEditingContent(values: $values) }
            .navigationTitle("Library editor").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar).toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
private struct LibraryEditingContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: EditingValues
    private var check: CheckState {
        let ids = Set(values.visible.map(\.id))
        return ids.isEmpty || ids.isDisjoint(with: values.selected) ? .off : ids.isSubset(of: values.selected) ? .on : .mixed
    }
    var body: some View {
        List {
            Section("Collection tags") { LibraryTags(values: $values) }.listRowBackground(t.colors.surfacePanel.color)
            Section {
                SearchField("Filter library", text: $values.search, clearLabel: "Clear library filter")
                Checkbox("Select visible items", state: check, stateDescription: check == .on ? "All visible selected" : check == .mixed ? "Some visible selected" : "None visible selected",
                         enabled: values.enabled && !values.visible.isEmpty, onToggle: { values.selectVisible() })
                Text("Visible items: \(values.visible.count) · Archived: \(values.archived.subtracting(values.removed).count)")
                Text("Swipe toward the end to archive, or toward the start to remove. Row menus offer the same actions. Local preview only.")
                    .font(t.typography.caption)
            }.listRowBackground(t.colors.surfacePanel.color)
            Section("Library items") {
                if values.visible.isEmpty { Text("No matching library items. Clear the filter or undo removal.") }
                ForEach(values.visible) { item in
                    SwipeActionRow(leading: SwipeAction(archiveLabel(item), onPerform: { values.toggleArchive(item.id) }),
                        trailing: SwipeAction("Remove \(item.title)", destructive: true, onPerform: { values.remove([item.id]) }), enabled: values.enabled) {
                        VStack(alignment: .leading, spacing: t.space.inline) {
                            SelectionRow(item.title, subtitle: values.archived.contains(item.id) ? "Archived" : "Local study",
                                selected: values.selected.contains(item.id), stateDescription: values.selected.contains(item.id) ? "Selected" : "Not selected",
                                enabled: values.enabled, onToggle: { values.toggle(item.id) })
                            ActionMenu("Actions for \(item.title)", actions: [
                                MenuAction(id: "archive", title: archiveLabel(item), enabled: values.enabled, onSelect: { values.toggleArchive(item.id) }),
                                MenuAction(id: "remove", title: "Remove \(item.title)", destructive: true, enabled: values.enabled, onSelect: { values.remove([item.id]) })
                            ])
                        }
                    }.listRowBackground(t.colors.surfacePanel.color)
                }
            }
        }.scrollContentBackground(.hidden).scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: t.space.inline) {
                    if !values.undoIDs.isEmpty {
                        ToastBanner("Removed \(values.undoIDs.count) preview items", action: values.enabled ? ToastAction("Undo removal", onPerform: { values.undo() }) : nil,
                                    dismissLabel: "Dismiss removal notice", onDismiss: { values.undoIDs = []; values.undoSelection = [] })
                    }
                    ActionBar(summary: "\(values.selected.count) selected · \(values.hiddenSelected) hidden") {
                        WrapLayout {
                            ActionButton("Archive selected", variant: .secondary, enabled: values.enabled && !values.selected.isEmpty) { values.archive(values.selected) }
                            ActionButton("Remove selected", variant: .destructive, enabled: values.enabled && !values.selected.isEmpty) { values.remove(values.selected) }
                            ActionButton("Clear selection", variant: .quiet, enabled: values.enabled && !values.selected.isEmpty) { values.selected = [] }
                        }
                    }
                }.padding(12)
            }
    }
    private func archiveLabel(_ item: LibraryPreviewItem) -> String { "\(values.archived.contains(item.id) ? "Unarchive" : "Archive") \(item.title)" }
}
