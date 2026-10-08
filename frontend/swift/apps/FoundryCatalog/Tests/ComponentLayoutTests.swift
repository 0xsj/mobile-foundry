import FoundryUI
import Observation
import SwiftUI
import Testing

@Observable @MainActor private final class LayoutProbeValues {
    var width: CGFloat = 360
    var typeSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}

private struct ComponentFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

private struct FrameProbe: ViewModifier {
    let name: String
    func body(content: Content) -> some View {
        content.background {
            GeometryReader { geometry in
                Color.clear.preference(key: ComponentFrames.self, value: [name: geometry.frame(in: .named("probe"))])
            }
        }
    }
}

private struct GridProbe: View {
    let values: LayoutProbeValues
    var body: some View {
        FoundryTheme {
            AdaptiveGrid(minimumItemWidth: 100, spacing: 12) {
                ForEach(0..<4) { index in
                    VStack {
                        Text("Item \(index)")
                        Color.clear.frame(height: index == 1 ? 100 : 20)
                    }.modifier(FrameProbe(name: "\(index)"))
                }
            }.frame(width: values.width)
        }
        .environment(\.dynamicTypeSize, values.typeSize)
        .environment(\.layoutDirection, values.direction)
        .coordinateSpace(name: "probe")
        .onPreferenceChange(ComponentFrames.self) { values.frames = $0 }
    }
}

@MainActor @Test func nativeComponentGridReflowsForWidthTextSizeAndDirection() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
    let values = LayoutProbeValues()
    let host = UIHostingController(rootView: GridProbe(values: values))
    window.rootViewController = host
    window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ stage: String, _ condition: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Native component geometry did not settle at \(stage): \(values.frames)")
    }
    try await settle("initial") { values.frames.count == 4 }
    let first = try #require(values.frames["0"])
    let tall = try #require(values.frames["1"])
    #expect(abs(first.minY - tall.minY) < 1)
    #expect(try #require(values.frames["3"]).minY > tall.maxY)
    values.width = 220
    try await settle("narrow") { (values.frames["0"]?.width ?? 0) < 110 }
    #expect(try #require(values.frames["2"]).minY > (values.frames["1"]?.maxY ?? 0))
    values.width = 360; values.typeSize = .accessibility3
    try await settle("large text") { (values.frames["0"]?.width ?? 0) > 350 }
    #expect(try #require(values.frames["1"]).minY > (values.frames["0"]?.maxY ?? 0))
    values.typeSize = .large; values.direction = .rightToLeft
    try await settle("RTL") { (values.frames["0"]?.minX ?? 0) > (values.frames["1"]?.minX ?? 0) }
    #expect(try #require(values.frames["0"]).minX > (values.frames["1"]?.minX ?? 0))
}

@MainActor @Test func nativeReadableContainerIncludesInsetsAndMediaPreservesRatio() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
    let values = LayoutProbeValues()
    let host = UIHostingController(rootView:
        FoundryTheme {
            ContentContainer(maximumWidth: 240, inset: 10) {
                MediaFrame(ratio: 4 / 3) { Color.red }.modifier(FrameProbe(name: "media"))
            }.frame(width: 360).modifier(FrameProbe(name: "outer"))
        }.coordinateSpace(name: "probe").onPreferenceChange(ComponentFrames.self) { values.frames = $0 })
    window.rootViewController = host
    window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    for _ in 0..<100 {
        host.view.layoutIfNeeded()
        if values.frames.count == 2 { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let media = try #require(values.frames["media"])
    let outer = try #require(values.frames["outer"])
    #expect(abs(media.width - 220) < 1)
    #expect(abs(media.width / media.height - 4 / 3) < 0.01)
    #expect(abs(media.midX - outer.midX) < 1)
}
