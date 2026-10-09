@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func fileProjectionRetainsIdentityAndRejectsHiddenUnavailableOrDisabledCommands() {
    var values = FileBrowserValues()
    #expect(values.entries.map(\.id) == ["atlas", "studies", "refs", "lens", "brief", "notes", "locked"])
    values.open("field"); values.open("locked"); values.open("missing"); values.favorite("locked"); values.toggle("lens")
    #expect(values.opens == 0 && values.selected == nil && values.favorites.isEmpty)
    values.collapseAll(); values.toggle("refs")
    #expect(values.entries.map(\.id) == ["atlas", "notes", "locked"] && values.expanded.isEmpty)
    values.search(" FIELD ")
    #expect(values.entries.map(\.id) == ["atlas", "studies", "refs", "field", "notes"])
    #expect(values.entries.map(\.depth) == [0, 1, 2, 3, 0] && values.isExpanded("refs"))
    values.toggle("atlas"); values.collapseAll(); #expect(values.expanded.isEmpty)
    values.favorite("field"); values.open("field")
    #expect(values.opens == 1 && values.selected == "field" && values.canInspect)
    values.search("")
    #expect(values.entries.map(\.id) == ["atlas", "notes", "locked"] && values.canInspect && values.selected == "field")
    values.favorite("field"); #expect(values.favorites.isEmpty)
    values.favorite("field"); values.search("not present"); #expect(values.entries.isEmpty && values.canInspect)
    values.empty = true; let empty = values
    values.open("notes"); values.favorite("notes"); values.toggle("atlas"); values.collapseAll()
    #expect(values == empty && !values.canInspect)
    values.empty = false; values.enabled = false; let disabled = values
    values.search(""); values.resetBrowser(); values.open("notes"); values.favorite("notes"); values.toggle("atlas"); values.collapseAll()
    #expect(values == disabled && !values.canInspect)
    values.enabled = true; values.resetBrowser()
    #expect(values.query.isEmpty && values.selected == nil && values.opens == 1 && values.favorites == ["field"] && values.entries.count == 7)
    #expect(BrowserItem.find("field")?.path == "Atlas / Studies / References / Field image.jpg")
}
@Observable @MainActor private final class FileProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct FileFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func fileFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: FileFrames.self, value: [id: proxy.frame(in: .named("files-probe"))]) } }
    }
}
private struct FileProbe: View {
    @Bindable var values: FileProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    TreeRow("A detailed reference image for the next project", subtitle: "Supplied metadata grows vertically", accessibilityLabel: "Open reference image",
                            depth: Int.max, selected: true, disclosure: .init(expanded: true, actionLabel: "Collapse references", stateLabel: "Expanded", onToggle: {}), onOpen: {}, leading: {
                        FileTypeMark("PNG", accessibilityLabel: "PNG image").fileFrame("mark")
                    }, actions: { ActionButton("Favorite reference image", variant: .quiet) {}.fileFrame("favorite") }).fileFrame("row")
                    TreeRow("Root item", accessibilityLabel: "Open root", onOpen: {})
                }
            }.frame(width: 240).coordinateSpace(name: "files-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(FileFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func deepTreeIndentationIsBoundedAndLargeTextArtworkAndActionsFitInRTL() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 3000)
    let values = FileProbeValues()
    let host = UIHostingController(rootView: FileProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Files geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 3 }
    let normal = try #require(values.frames["row"])
    values.textSize = .accessibility3
    try await settle { (values.frames["row"]?.height ?? 0) > normal.height + 80 }
    let row = try #require(values.frames["row"]), action = try #require(values.frames["favorite"]), mark = try #require(values.frames["mark"])
    #expect(row.width <= 241 && action.width >= 44 && action.height >= 44 && mark.width >= 40)
    #expect(action.minX >= row.minX + 47 && action.maxX <= row.maxX + 1)
    #expect(mark.minX >= row.minX && mark.maxX <= row.maxX + 1)
    values.direction = .rightToLeft
    try await settle { (values.frames["favorite"]?.maxX ?? 1000) <= row.maxX - 47 }
    let rtlAction = try #require(values.frames["favorite"]), rtlMark = try #require(values.frames["mark"])
    #expect(rtlAction.minX >= row.minX - 1 && rtlAction.maxX <= row.maxX - 47)
    #expect(rtlMark.minX >= row.minX - 1 && rtlMark.maxX <= row.maxX + 1)
}
