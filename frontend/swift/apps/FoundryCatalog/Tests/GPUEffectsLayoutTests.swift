import FoundryGraphics
import MetalKit
import SwiftUI
import Testing
@testable import FoundryCatalog

@MainActor @Test func hostedCatalogAllocatesAFullCanvasDrawable() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let window = UIWindow(windowScene: scene)
    let previousKey = scene.keyWindow
    window.frame = CGRect(x: 0, y: 0, width: 360, height: 800)
    let host = UIHostingController(rootView: AnyView(GPUEffectsView()))
    window.rootViewController = host
    window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previousKey?.makeKey() }
    func metal(in view: UIView) -> MTKView? {
        if let view = view as? MTKView { return view }
        return view.subviews.lazy.compactMap { metal(in: $0) }.first
    }
    var canvas: MTKView?
    for _ in 0..<100 {
        host.view.layoutIfNeeded()
        canvas = metal(in: host.view)
        if let canvas, canvas.drawableSize.width > 100 { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let view = try #require(canvas)
    #expect(view.bounds.width > 250)
    #expect(view.bounds.height > 200)
    let target = try #require(EffectQuality.balanced.resolution(
        width: view.bounds.width * scene.screen.nativeScale,
        height: view.bounds.height * scene.screen.nativeScale))
    #expect(view.drawableSize == CGSize(width: target.width, height: target.height))
    let drawable = try #require(view.currentDrawable)
    #expect(drawable.texture.width == target.width)
    #expect(drawable.texture.height == target.height)
    // Automatic MTKView drawing must report the real target too. Reading a
    // drawable outside a frame alone misses resizing after frame acquisition.
    var reports: [EffectStatistics] = []
    func surface(running: Bool, quality: EffectQuality = .balanced) -> AnyView {
        AnyView(MetalEffectSurface(settings: .init(quality: quality), running: running, onPoint: { _ in }, onEvent: {
            if case .statistics(let value) = $0 { reports.append(value) }
            if case .failed = $0 { Issue.record("GPU initialization failed") }
        }, onUnexpectedError: { Issue.record("\($0)") }).frame(width: 300, height: 250))
    }
    host.rootView = surface(running: true)
    for _ in 0..<150 {
        if !reports.isEmpty { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let report = try #require(reports.last)
    let automaticTarget = try #require(EffectQuality.balanced.resolution(
        width: 300 * scene.screen.nativeScale, height: 250 * scene.screen.nativeScale))
    #expect(report.width == automaticTarget.width && report.height == automaticTarget.height)
    host.rootView = surface(running: false, quality: .economy)
    try await Task.sleep(for: .milliseconds(100))
    let economyTarget = try #require(EffectQuality.economy.resolution(
        width: 300 * scene.screen.nativeScale, height: 250 * scene.screen.nativeScale))
    #expect(reports.last?.width == economyTarget.width && reports.last?.height == economyTarget.height)
    let pausedCount = reports.last?.submittedFrames
    try await Task.sleep(for: .milliseconds(150))
    #expect(reports.last?.submittedFrames == pausedCount)
    let activeView = try #require(metal(in: host.view))
    #expect(activeView.isPaused)
    host.rootView = surface(running: true)
    for _ in 0..<150 {
        if let count = reports.last?.submittedFrames, count > (pausedCount ?? 0) { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    #expect((reports.last?.submittedFrames ?? 0) > (pausedCount ?? 0))
    // Removal exercises the native representable's dismantle path.
    host.rootView = AnyView(EmptyView())
    for _ in 0..<100 {
        if activeView.delegate == nil { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    #expect(activeView.delegate == nil)
    #expect(activeView.isPaused)
}
