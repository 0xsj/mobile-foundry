@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func pageValuesOutliveNavigationAndIgnoreDuplicateOrUnknownDestinations() {
    var values = ShellValues()
    values.addMarker(); values.select("Activity"); values.addMarker(); values.addMarker()
    let changed = values
    values.select("Activity"); values.select("missing"); #expect(values == changed)
    values.showNavigation = false; values.spacing = .relaxed
    values.select("Overview"); #expect(values.markers[.overview] == 1 && values.markers[.activity] == 2)
    values.showNavigation = true; #expect(values.selections == 2 && values.spacing == .relaxed)
}

@Observable @MainActor private final class ShellProbeValues {
    var spacing: CGFloat? = nil
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
    var selection = "Overview"
}
private struct ShellFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func shellFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: ShellFrames.self, value: [id: proxy.frame(in: .named("shell-probe"))]) } }
    }
}
private struct ShellLayoutProbe: View {
    @Bindable var values: ShellProbeValues
    var body: some View {
        FoundryTheme {
            AppShell(background: { Color.clear }, content: {
                VerticalStack(spacing: values.spacing) {
                    Color.blue.frame(width: 40, height: 20).shellFrame("first")
                    Color.blue.frame(width: 40, height: 20).shellFrame("second")
                    HorizontalStack(spacing: values.spacing) {
                        Color.blue.frame(width: 40, height: 20).shellFrame("left")
                        Color.blue.frame(width: 40, height: 20).shellFrame("right")
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    SectionDivider(inset: 8, thickness: 2).shellFrame("divider")
                }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).shellFrame("content")
            }, navigation: {
                Text("Supplied navigation").frame(maxWidth: .infinity).frame(height: 60).shellFrame("navigation")
            }).frame(width: 240, height: 500).shellFrame("viewport")
                .coordinateSpace(name: "shell-probe")
        }.environment(\.layoutDirection, values.direction).environment(\.dynamicTypeSize, .accessibility3)
            .onPreferenceChange(ShellFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func tokenStackSpacingAndShellSlotsKeepNavigationOutsideContent() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene), previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 900)
    let values = ShellProbeValues(), host = UIHostingController(rootView: ShellLayoutProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Shell geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 8 }
    let first = try #require(values.frames["first"]), second = try #require(values.frames["second"])
    #expect(abs(second.minY - first.maxY - 12) < 1)
    let left = try #require(values.frames["left"]), right = try #require(values.frames["right"])
    #expect(abs(right.minX - left.maxX - 8) < 1)
    let viewport = try #require(values.frames["viewport"]), content = try #require(values.frames["content"]), nav = try #require(values.frames["navigation"])
    #expect(abs(nav.maxY - viewport.maxY) < 1 && abs(content.maxY - nav.minY) < 1)
    #expect(abs(nav.height - 60) < 1 && abs(content.height - 440) < 1)
    #expect(abs((values.frames["divider"]?.height ?? 0) - 2) < 1)
    values.spacing = 24
    try await settle { abs((values.frames["second"]?.minY ?? 0) - (values.frames["first"]?.maxY ?? 0) - 24) < 1 }
    #expect(abs((values.frames["right"]?.minX ?? 0) - (values.frames["left"]?.maxX ?? 0) - 24) < 1)
    values.direction = .rightToLeft
    try await settle { (values.frames["left"]?.minX ?? 0) > (values.frames["right"]?.minX ?? 0) }
    #expect((values.frames["left"]?.maxX ?? 1000) <= viewport.maxX + 1)
}
private struct NativeTabProbe: View {
    @Bindable var values: ShellProbeValues
    var body: some View {
        FoundryTheme {
            AppShell(background: { Color.clear }, content: {
                TabBar(items: ShellDestination.allCases.map { destination in
                    TabItem(id: destination.rawValue, label: destination.rawValue) { Image(systemName: destination.symbol) }
                }, selectedId: $values.selection) { id in Text("\(id) page") }
            })
        }
    }
}
@MainActor @Test func suppliedIdentityControlsNativeTabChrome() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene), previous = scene.keyWindow
    let window = UIWindow(windowScene: scene), values = ShellProbeValues()
    let host = UIHostingController(rootView: NativeTabProbe(values: values)); window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func tabBar(_ view: UIView) -> UITabBar? {
        if let bar = view as? UITabBar { return bar }
        for child in view.subviews { if let bar = tabBar(child) { return bar } }
        return nil
    }
    func settle(_ title: String) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if tabBar(host.view)?.selectedItem?.title == title { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Native tab selection did not settle")
    }
    try await settle("Overview")
    let bar = try #require(tabBar(host.view))
    #expect(bar.items?.compactMap(\.title) == ShellDestination.allCases.map(\.rawValue))
    #expect(bar.items?.allSatisfy { $0.image != nil } == true)
    values.selection = "Settings"; try await settle("Settings")
    values.selection = "Activity"; try await settle("Activity")
    #expect(values.selection == "Activity")
}
