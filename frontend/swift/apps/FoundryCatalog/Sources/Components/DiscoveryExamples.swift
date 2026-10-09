import FoundryUI
import SwiftUI

enum DiscoveryTopic: String, CaseIterable { case all = "All topics", graphics = "Graphics", writing = "Writing", design = "Design" }
struct DiscoveryFilters: Equatable { var topic: DiscoveryTopic = .all; var archived = false }
struct DiscoveryRecord: Identifiable {
    let id: String; let title: String; let excerpt: String; let topic: DiscoveryTopic; let archived: Bool
    static let all = [
        Self(id: "motion", title: "Motion study", excerpt: "Explore motion, light and material in a small scene.", topic: .graphics, archived: false),
        Self(id: "metal", title: "Metal notes", excerpt: "Notes on shaders, textures and GPU frame ownership.", topic: .graphics, archived: false),
        Self(id: "field", title: "Offline field notes", excerpt: "Collect notes offline and review them when connected.", topic: .writing, archived: false),
        Self(id: "sketch", title: "Sketch archive", excerpt: "Archived sketches of interfaces and motion experiments.", topic: .design, archived: true),
        Self(id: "interface", title: "Interface patterns", excerpt: "Reusable forms, navigation and accessible interactions.", topic: .design, archived: false),
        Self(id: "sync", title: "Sync checklist", excerpt: "A checklist for syncing notes without losing edits.", topic: .writing, archived: false)
    ]
}
struct DiscoveryValues {
    var query = ""
    var filters = DiscoveryFilters()
    var enabled = true
    var saved = Set<String>()
    var recent: [String] = []
    var opened: String?
    var opens = 0
    var term: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }
    var results: [DiscoveryRecord] { DiscoveryRecord.all.filter { row in
        (filters.archived || !row.archived) && (filters.topic == .all || filters.topic == row.topic) &&
        (term.isEmpty || "\(row.title) \(row.excerpt) \(row.topic.rawValue)".range(of: term, options: .caseInsensitive) != nil)
    } }
    var openedTitle: String { DiscoveryRecord.all.first { $0.id == opened }?.title ?? "No record yet" }
    mutating func setQuery(_ value: String) { guard enabled else { return }; query = value }
    mutating func useSuggestion(_ value: String) { guard enabled && !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }; query = value; rememberQuery() }
    mutating func rememberQuery() {
        guard enabled && !term.isEmpty else { return }
        let value = term
        recent = [value] + recent.filter { $0.caseInsensitiveCompare(value) != .orderedSame }.prefix(2)
    }
    mutating func clearRecent() { guard enabled else { return }; recent = [] }
    mutating func apply(_ draft: DiscoveryFilters) { guard enabled else { return }; filters = draft }
    mutating func open(_ id: String) { guard enabled && results.contains(where: { $0.id == id }) else { return }; opened = id; opens += 1; rememberQuery() }
    mutating func toggleSaved(_ id: String) {
        guard enabled && results.contains(where: { $0.id == id }) else { return }
        if saved.contains(id) { saved.remove(id) } else { saved.insert(id) }
    }
}
/// The fixture marks only its first case-insensitive literal match; reusable text receives segments, not offsets.
func discoverySegments(_ text: String, query: String) -> [HighlightSegment] {
    let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !term.isEmpty, let range = text.range(of: term, options: .caseInsensitive) else { return [HighlightSegment(text)] }
    return [HighlightSegment(String(text[..<range.lowerBound])), HighlightSegment(String(text[range]), highlighted: true),
            HighlightSegment(String(text[range.upperBound...]))]
}
struct DiscoveryExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: DiscoveryValues
    var body: some View {
        Card {
            SectionHeader("Search and discovery", subtitle: "Useful suggestions, readable results and filters you apply explicitly.")
            NavLink("Open search workspace", subtitle: "Search local records, save a result and compare filter drafts") {
                DiscoveryPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        DiscoveryContent(values: $values)
    }
}
struct DiscoveryPreview: View {
    @Binding var values: DiscoveryValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { DiscoveryContent(values: $values).padding(20) }.scrollDismissesKeyboard(.interactively) }
            .navigationTitle("Search workspace").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct DiscoveryContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: DiscoveryValues
    @State private var showFilters = false
    @State private var draft = DiscoveryFilters()
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable discovery actions", isOn: $values.enabled)
                SearchField("Search library", text: Binding(get: { values.query }, set: { values.setQuery($0) }),
                            clearLabel: "Clear search", enabled: values.enabled, onSubmit: { values.rememberQuery() })
                ActionButton("Filters", variant: .secondary, enabled: values.enabled) { draft = values.filters; showFilters = true }
                Text("Applied topic: \(values.filters.topic.rawValue)").font(t.typography.caption)
                Text(values.filters.archived ? "Including archived records" : "Active records only").font(t.typography.caption)
                WrapLayout {
                    if values.filters.topic != .all {
                        RemovableChip(values.filters.topic.rawValue, removeLabel: "Remove topic filter", enabled: values.enabled) {
                            var filters = values.filters; filters.topic = .all; values.apply(filters)
                        }
                    }
                    if values.filters.archived {
                        RemovableChip("Archived", removeLabel: "Remove archived filter", enabled: values.enabled) {
                            var filters = values.filters; filters.archived = false; values.apply(filters)
                        }
                    }
                }
            }
            if values.term.isEmpty {
                Card {
                    SectionHeader("Suggested searches")
                    ForEach(["motion", "notes", "metal"], id: \.self) { term in
                        SearchSuggestionRow(term, detail: "Explore the library", accessibilityLabel: "Search for \(term)", enabled: values.enabled,
                                            onUse: { values.useSuggestion(term) }) { Image(systemName: "magnifyingglass") }
                    }
                    if !values.recent.isEmpty {
                        SectionHeader("Recent searches")
                        ForEach(values.recent, id: \.self) { term in
                            SearchSuggestionRow(term, detail: "Recent search", accessibilityLabel: "Search again for \(term)", enabled: values.enabled,
                                                onUse: { values.useSuggestion(term) }) { Image(systemName: "clock") }
                        }
                        ActionButton("Clear recent searches", variant: .quiet, enabled: values.enabled) { values.clearRecent() }
                    }
                }
            }
            SectionHeader("Results", subtitle: "\(values.results.count) records")
            if values.results.isEmpty { Card { EmptyState("No matching records", message: "Try another search or change the applied filters.") } }
            ForEach(values.results) { row in
                Card {
                    SearchResultRow(row.title, detail: "\(row.topic.rawValue)\(row.archived ? " · Archived" : "")",
                                    accessibilityLabel: "Open \(row.title). \(row.topic.rawValue). \(row.excerpt)", enabled: values.enabled,
                                    onOpen: { values.open(row.id) }, leading: { Image(systemName: "doc.text").font(.title2) }, preview: {
                        HighlightedText(discoverySegments(row.excerpt, query: values.term))
                    }, actions: {
                        ActionButton(values.saved.contains(row.id) ? "Saved" : "Save", variant: .quiet, enabled: values.enabled) { values.toggleSaved(row.id) }
                            .accessibilityLabel("\(values.saved.contains(row.id) ? "Unsave" : "Save") \(row.title)")
                            .accessibilityValue(values.saved.contains(row.id) ? "Saved" : "Not saved")
                    })
                }
            }
            Card(.floating) {
                KeyValueRow("Last opened", value: values.openedTitle)
                Text("Records opened: \(values.opens)").font(t.typography.caption)
                Text("Saved records: \(values.saved.count)").font(t.typography.caption)
                Text("Local library preview. Searches and bookmarks stay in this example.").font(t.typography.caption)
            }
        }
        .sheetPanel("Filter records", isPresented: $showFilters, closeLabel: "Discard filters") {
            ScrollView {
                VStack(alignment: .leading, spacing: t.space.section) {
                    SelectField("Topic", selection: $draft.topic, options: DiscoveryTopic.allCases, enabled: values.enabled, label: { $0.rawValue })
                    ToggleField("Include archived", isOn: $draft.archived, enabled: values.enabled)
                    Text("Only Apply filters changes the results.").font(t.typography.caption)
                    ActionButton("Reset filter draft", variant: .quiet, enabled: values.enabled) { draft = DiscoveryFilters() }
                    ActionButton("Apply filters", enabled: values.enabled) { values.apply(draft); showFilters = false }
                }
            }.presentationDetents([.large])
        }
        .onChange(of: values.enabled) { _, enabled in if !enabled { showFilters = false } }
    }
}
