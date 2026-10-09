@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func discoveryProjectsResultsWithoutLosingIdentityAndPreservesLiteralText() {
    var values = DiscoveryValues()
    values.toggleSaved("motion"); values.open("motion")
    values.apply(DiscoveryFilters(topic: .writing, archived: true))
    values.toggleSaved("motion"); values.open("motion")
    #expect(values.results.map(\.id) == ["field", "sync"])
    #expect(values.saved == ["motion"] && values.openedTitle == "Motion study" && values.opens == 1)
    values.setQuery(" missing "); #expect(values.results.isEmpty)
    values.useSuggestion(" motion "); values.useSuggestion("notes"); values.useSuggestion("metal"); values.useSuggestion(" NOTES ")
    #expect(values.recent == ["NOTES", "metal", "motion"])
    values.enabled = false
    values.setQuery(""); values.useSuggestion("design"); values.clearRecent(); values.apply(DiscoveryFilters())
    values.toggleSaved("field"); values.open("field")
    #expect(values.filters.topic == .writing && values.query == " NOTES " && values.saved == ["motion"] && values.opens == 1)
    values.enabled = true; values.apply(DiscoveryFilters()); values.setQuery("motion")
    #expect(values.results.map(\.id) == ["motion"])
    values.apply(DiscoveryFilters(archived: true)); #expect(values.results.map(\.id) == ["motion", "sketch"])
    let text = "👨‍👩‍👧‍👦 café <b>Notes</b> & notes"
    let segments = discoverySegments(text, query: " notes ")
    #expect(segments.map(\.text).joined() == text)
    #expect(segments.filter(\.highlighted).map(\.text) == ["Notes"])
    #expect(discoverySegments(text, query: "").map(\.text).joined() == text)
    #expect(discoverySegments(text, query: "absent").allSatisfy { !$0.highlighted })
}
@Observable @MainActor private final class DiscoveryProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct DiscoveryFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func discoveryFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: DiscoveryFrames.self, value: [id: proxy.frame(in: .named("discovery-probe"))]) } }
    }
}
private struct DiscoveryProbe: View {
    @Bindable var values: DiscoveryProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SearchSuggestionRow("Explore longer interface patterns", detail: "Search suggestions wrap with their supplied text",
                                        accessibilityLabel: "Search for interface patterns", onUse: {}) {
                        Image(systemName: "magnifyingglass").discoveryFrame("leading")
                    }.discoveryFrame("suggestion")
                    SearchResultRow("A longer library result title", detail: "Design · Archived", accessibilityLabel: "Open the design record", onOpen: {},
                                    leading: { Image(systemName: "doc.text") }, preview: {
                        HighlightedText([HighlightSegment("Read "), HighlightSegment("literal text", highlighted: true),
                                         HighlightSegment(" and keep the complete sentence readable.")]).discoveryFrame("excerpt")
                    }, actions: { ActionButton("Save result", variant: .quiet) {}.discoveryFrame("save") }).discoveryFrame("result")
                }
            }.frame(width: 240).coordinateSpace(name: "discovery-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(DiscoveryFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func discoveryRowsWrapAndKeepSiblingActionsOutsideTheOpenRegion() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 1800)
    let values = DiscoveryProbeValues()
    let host = UIHostingController(rootView: DiscoveryProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Discovery geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 5 }
    let normal = try #require(values.frames["result"])
    values.textSize = .accessibility3
    try await settle { (values.frames["result"]?.height ?? 0) > normal.height + 40 }
    let result = try #require(values.frames["result"]), excerpt = try #require(values.frames["excerpt"]), save = try #require(values.frames["save"])
    #expect(result.width <= 241 && save.width >= 44 && save.height >= 44)
    #expect(save.minY >= excerpt.maxY && save.minX >= result.minX - 1 && save.maxX <= result.maxX + 1)
    let leading = try #require(values.frames["leading"])
    values.direction = .rightToLeft
    try await settle { (values.frames["leading"]?.minX ?? 0) > leading.minX + 100 }
}
