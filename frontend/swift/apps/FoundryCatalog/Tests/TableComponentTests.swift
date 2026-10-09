@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func ledgerSortPagingAndEmptyProjectionKeepIdentityAndGuardActions() {
    var values = TableValues()
    #expect(values.rows.map(\.id) == ["atlas", "delta", "field"])
    values.inspect("orbit"); #expect(values.inspections == 0)
    values.inspect("atlas"); values.changePage(3); values.sort(.sessions)
    #expect(values.page == 3 && values.rows.map(\.id) == ["studio", "meadow", "vista"])
    #expect(values.inspected == "atlas" && values.inspections == 1)
    values.sort(.sessions)
    #expect(values.rows.map(\.id) == ["harbor", "orbit", "delta"])
    values.empty = true; values.changePage(2); values.inspect("harbor")
    #expect(values.page == 3 && values.shownPage == 1 && values.rows.isEmpty && values.inspections == 1)
    values.enabled = false; values.sort(.project); values.empty = false; values.changePage(1); values.inspect("harbor")
    #expect(values.sortKey == .sessions && values.order == .descending && values.page == 3 && values.inspections == 1)
    values.enabled = true; values.changePage(0); values.changePage(4)
    #expect(values.page == 3)
    values.sort(.status)
    #expect(values.sorted.map(\.id) == ["field", "lumen", "vista", "atlas", "meadow", "studio", "delta", "harbor", "orbit"])
    values.sort(.status)
    #expect(values.sorted.map(\.id) == ["delta", "harbor", "orbit", "atlas", "meadow", "studio", "field", "lumen", "vista"])
}
@Observable @MainActor private final class TableProbeValues {
    var size: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct TableFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func tableFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: TableFrames.self, value: [id: proxy.frame(in: .named("table-probe"))]) } }
    }
}
private struct TableProbe: View {
    @Bindable var values: TableProbeValues
    let columns = [DataTableColumn(id: "project", label: "Project", width: 240), DataTableColumn(id: "status", label: "Status", width: 160)]
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    DataTable("Probe records", rows: [LedgerRecord.all[0]], columns: columns, header: { column in
                        Text(column.label).frame(maxWidth: .infinity, alignment: .leading).tableFrame("header:\(column.id)")
                    }, cell: { row, column in
                        Text(column.id == "project" ? "A much longer project title that grows vertically" : "Ready for review")
                            .fixedSize(horizontal: false, vertical: true).frame(maxWidth: .infinity, alignment: .leading).tableFrame("cell:\(column.id)")
                    }).tableFrame("table")
                    PaginationBar(page: 1, totalPages: 3, pageLabel: "Page 1 of 3", previousLabel: "Previous page", nextLabel: "Next page") { _ in }.tableFrame("pages")
                }
            }.frame(width: 240).coordinateSpace(name: "table-probe")
        }.environment(\.dynamicTypeSize, values.size).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(TableFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func tableColumnsStayAlignedThroughNativeScrollLargeTextAndRTL() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 1500)
    let values = TableProbeValues()
    let host = UIHostingController(rootView: TableProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Table geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 6 }
    let normal = values.frames
    #expect((values.frames["table"]?.width ?? 0) <= 241)
    for column in ["project", "status"] {
        let header = try #require(values.frames["header:\(column)"]), cell = try #require(values.frames["cell:\(column)"])
        #expect(abs(header.minX - cell.minX) < 1 && abs(header.width - cell.width) < 1)
    }
    func scrollViews(_ view: UIView) -> [UIScrollView] {
        ((view as? UIScrollView).map { [$0] } ?? []) + view.subviews.flatMap(scrollViews)
    }
    let horizontal = try #require(scrollViews(host.view).first { $0.contentSize.width > $0.bounds.width + 100 })
    horizontal.setContentOffset(CGPoint(x: 120, y: 0), animated: false)
    try await settle { (values.frames["header:project"]?.minX ?? 0) < (normal["header:project"]?.minX ?? 0) - 100 }
    #expect(abs((values.frames["header:status"]?.minX ?? 0) - (values.frames["cell:status"]?.minX ?? 10)) < 1)
    values.size = .accessibility3
    try await settle { (values.frames["table"]?.height ?? 0) > (normal["table"]?.height ?? 0) + 40 }
    #expect((values.frames["pages"]?.width ?? 0) <= 241)
    #expect((values.frames["pages"]?.height ?? 0) > (normal["pages"]?.height ?? 0))
    values.direction = .rightToLeft
    try await settle { (values.frames["header:project"]?.minX ?? 0) > (values.frames["header:status"]?.minX ?? 1000) }
    #expect(abs((values.frames["header:project"]?.minX ?? 0) - (values.frames["cell:project"]?.minX ?? 10)) < 1)
}
