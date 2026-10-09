import SwiftUI

public struct DataTableColumn: Identifiable, Sendable {
    public let id: String
    public let label: String
    /// Total column width, including the table's native cell padding.
    public let width: CGFloat
    public init(id: String, label: String, width: CGFloat = 160) {
        precondition(width.isFinite && width > 0)
        self.id = id; self.label = label; self.width = width
    }
}

/// Small eager tables of supplied rows. A bounded horizontal viewport preserves column widths.
/// The host owns vertical scrolling, sorting, paging and contextual cell narration. Slots may contain native controls.
public struct DataTable<Row: Identifiable, Header: View, Cell: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let rows: [Row]
    private let columns: [DataTableColumn]
    private let header: (DataTableColumn) -> Header
    private let cell: (Row, DataTableColumn) -> Cell
    public init(_ title: String, rows: [Row], columns: [DataTableColumn],
                @ViewBuilder header: @escaping (DataTableColumn) -> Header, @ViewBuilder cell: @escaping (Row, DataTableColumn) -> Cell) {
        precondition(!columns.isEmpty && Set(columns.map(\.id)).count == columns.count)
        precondition(Set(rows.map(\.id)).count == rows.count)
        precondition(columns.reduce(CGFloat.zero) { $0 + $1.width }.isFinite)
        self.title = title; self.rows = rows; self.columns = columns; self.header = header; self.cell = cell
    }
    public var body: some View {
        Surface {
            ScrollView(.horizontal) {
                VStack(spacing: 0) {
                    HStack(spacing: 0) {
                        ForEach(columns) { column in
                            header(column).padding(t.space.inline).frame(width: column.width, alignment: .leading)
                        }
                    }.frame(minHeight: t.shape.minimumInteractive).background(t.colors.surfaceSunk.color)
                    ForEach(rows) { row in
                        HStack(spacing: 0) {
                            ForEach(columns) { column in
                                cell(row, column).padding(t.space.inline).frame(width: column.width, alignment: .leading)
                            }
                        }.frame(minHeight: t.shape.minimumInteractive)
                            .overlay(alignment: .top) { Rectangle().fill(t.colors.line.color).frame(height: 1).accessibilityHidden(true) }
                            .accessibilityElement(children: .contain)
                    }
                }.fixedSize(horizontal: true, vertical: false)
            }
        }.clipShape(RoundedRectangle(cornerRadius: t.shape.panel))
            .accessibilityElement(children: .contain).accessibilityLabel(title)
    }
}
