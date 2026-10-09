import FoundryUI
import SwiftUI

enum TableSortKey: String, CaseIterable {
    case project = "Project", sessions = "Sessions", status = "Status"
    var id: String { rawValue.lowercased() }
}
struct LedgerRecord: Identifiable {
    let id: String; let title: String; let sessions: Int; let status: String
    static let all = [
        Self(id: "atlas", title: "Atlas workspace", sessions: 12, status: "Ready"),
        Self(id: "orbit", title: "Orbit study", sessions: 4, status: "Review"),
        Self(id: "field", title: "Field notes", sessions: 8, status: "Draft"),
        Self(id: "meadow", title: "Meadow journal", sessions: 16, status: "Ready"),
        Self(id: "harbor", title: "Harbor archive", sessions: 6, status: "Review"),
        Self(id: "lumen", title: "Lumen sketches", sessions: 10, status: "Draft"),
        Self(id: "delta", title: "Delta research", sessions: 2, status: "Review"),
        Self(id: "studio", title: "Studio collection", sessions: 14, status: "Ready"),
        Self(id: "vista", title: "Vista exploration", sessions: 18, status: "Draft")
    ]
}
struct TableValues {
    var sortKey: TableSortKey = .project
    var order: TableSortOrder = .ascending
    var page = 1
    var enabled = true
    var empty = false
    var inspected: String?
    var inspections = 0
    static let columns = [DataTableColumn(id: "project", label: "Project", width: 220), DataTableColumn(id: "sessions", label: "Sessions", width: 180),
                          DataTableColumn(id: "status", label: "Status", width: 150), DataTableColumn(id: "open", label: "Inspect", width: 130)]
    var sorted: [LedgerRecord] { LedgerRecord.all.sorted { left, right in
        let comparison: Int
        switch sortKey {
        case .project: comparison = left.title == right.title ? 0 : left.title < right.title ? -1 : 1
        case .sessions: comparison = left.sessions == right.sessions ? 0 : left.sessions < right.sessions ? -1 : 1
        case .status: comparison = left.status == right.status ? 0 : left.status < right.status ? -1 : 1
        }
        if comparison == 0 { return left.id < right.id }
        return order == .ascending ? comparison < 0 : comparison > 0
    } }
    var rows: [LedgerRecord] { empty ? [] : Array(sorted.dropFirst((page - 1) * 3).prefix(3)) }
    var totalPages: Int { empty ? 1 : 3 }
    var shownPage: Int { empty ? 1 : page }
    var orderLabel: String { order == .ascending ? "Ascending" : "Descending" }
    var inspectedTitle: String { LedgerRecord.all.first { $0.id == inspected }?.title ?? "No project yet" }
    mutating func sort(_ key: TableSortKey) {
        guard enabled else { return }
        if sortKey == key { order = order == .ascending ? .descending : .ascending } else { sortKey = key; order = .ascending }
    }
    mutating func changePage(_ value: Int) { guard enabled && !empty && (1...totalPages).contains(value) else { return }; page = value }
    mutating func inspect(_ id: String) { guard enabled && rows.contains(where: { $0.id == id }) else { return }; inspected = id; inspections += 1 }
}
struct TableExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: TableValues
    var body: some View {
        Card {
            SectionHeader("Tables and pages", subtitle: "Readable columns, explicit sorting and controlled pages.")
            NavLink("Open project ledger", subtitle: "Scroll across records, sort columns and inspect a page") {
                TablePreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        TableContent(values: $values)
    }
}
struct TablePreview: View {
    @Binding var values: TableValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            ScrollView { TableContent(values: $values).padding(20) }
        }.navigationTitle("Project ledger").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct TableContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: TableValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable ledger actions", isOn: $values.enabled)
                ToggleField("Show empty records", isOn: $values.empty)
                Text("Sorted by \(values.sortKey.rawValue), \(values.orderLabel.lowercased())").font(t.typography.caption)
                Text("Scroll horizontally to see every column. Sorting keeps the current page.").font(t.typography.caption)
            }
            DataTable("Project records", rows: values.rows, columns: TableValues.columns, header: { column in
                if let key = TableSortKey.allCases.first(where: { $0.id == column.id }) {
                    TableSortHeader(column.label, order: values.sortKey == key ? values.order : nil,
                                    accessibilityValue: values.sortKey == key ? values.orderLabel : "Not sorted", enabled: values.enabled) { values.sort(key) }
                        .accessibilityLabel("Sort by \(column.label)")
                } else { Text(column.label).font(t.typography.label).accessibilityAddTraits(.isHeader) }
            }, cell: { row, column in
                switch column.id {
                case "project": Text(row.title).font(t.typography.label).fixedSize(horizontal: false, vertical: true).accessibilityLabel("Project: \(row.title)")
                case "sessions": Text("\(row.sessions)").accessibilityLabel("\(row.title), sessions: \(row.sessions)")
                case "status": Badge(row.status).accessibilityLabel("\(row.title), status: \(row.status)")
                default: ActionButton("Inspect", variant: .quiet, enabled: values.enabled) { values.inspect(row.id) }.accessibilityLabel("Inspect \(row.title)")
                }
            })
            if values.empty { Card { EmptyState("No project records", message: "Turn off empty records to restore the retained page.") } }
            Card(.floating) {
                PaginationBar(page: values.shownPage, totalPages: values.totalPages, pageLabel: "Page \(values.shownPage) of \(values.totalPages)",
                              previousLabel: "Previous page", nextLabel: "Next page", enabled: values.enabled && !values.empty) { values.changePage($0) }
                KeyValueRow("Last inspected", value: values.inspectedTitle)
                Text("Inspections: \(values.inspections)").font(t.typography.caption)
                Text("Local fixture. Page controls and sort actions do not fetch records.").font(t.typography.caption)
            }
        }
    }
}
