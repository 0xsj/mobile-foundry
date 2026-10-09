@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Observable @MainActor private final class MediaPagingProbeValues {
    var selection = "orbit"
    var frames: [String: CGRect] = [:]
}
private struct MediaPageFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, latest in latest })
    }
}
private struct MediaPagingProbe: View {
    @Bindable var values: MediaPagingProbeValues
    var body: some View {
        FoundryTheme {
            Carousel(MediaStudy.all, selection: $values.selection) { study in
                study.colors[0].frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background { GeometryReader { geometry in
                        Color.clear.preference(key: MediaPageFrames.self,
                            value: [study.id: geometry.frame(in: .named("media-paging"))])
                    } }
            }.frame(width: 320, height: 220).coordinateSpace(name: "media-paging")
        }.onPreferenceChange(MediaPageFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func nativeMediaPagingAlignsStableSelectionToTheViewport() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
    let values = MediaPagingProbeValues()
    let host = UIHostingController(rootView: MediaPagingProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ id: String) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if let frame = values.frames[id], abs(frame.minX) < 2 && abs(frame.width - 320) < 2 { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Native media page \(id) did not align: \(values.frames)")
    }
    try await settle("orbit")
    values.selection = "arc"; try await settle("arc")
    #expect(values.selection == "arc")
    values.selection = "field"; try await settle("field")
    #expect(values.selection == "field")
}

@Observable @MainActor private final class MediaLayoutProbeValues {
    var height: CGFloat = 0
    var size: DynamicTypeSize = .large
}
private struct MediaTileHeight: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
private struct MediaLayoutProbe: View {
    @Bindable var values: MediaLayoutProbeValues
    var body: some View {
        FoundryTheme {
            MediaTile("A longer title for a small media collection", subtitle: "Supporting copy grows with native text size while actions remain independent.") {
                Color.blue
            } actions: {
                IconAction("Inspect media", action: {}) { Image(systemName: "arrow.up.right") }
                RatingField("Rate this study", value: .constant(2), valueLabel: "Two of five", optionLabel: { "Rate \($0) of five" })
            }.frame(width: 320)
                .background { GeometryReader { Color.clear.preference(key: MediaTileHeight.self, value: $0.size.height) } }
        }.environment(\.dynamicTypeSize, values.size)
            .onPreferenceChange(MediaTileHeight.self) { values.height = $0 }
    }
}
@MainActor @Test func mediaMetadataAndRatingCopyGrowWithAccessibilityText() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 1400)
    let values = MediaLayoutProbeValues()
    let host = UIHostingController(rootView: MediaLayoutProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    for _ in 0..<100 {
        host.view.layoutIfNeeded(); if values.height > 0 { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let height = values.height
    #expect(height > 0)
    values.size = .accessibility3
    for _ in 0..<100 {
        host.view.layoutIfNeeded(); if values.height > height + 50 { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    #expect(values.height > height + 50)
}
