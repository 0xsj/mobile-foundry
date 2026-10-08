import FoundryGraphics
import MetalKit
import SwiftUI
import Testing

@testable import FoundryCatalog

@MainActor @Test func hostedProductPreviewsDrawAtCanvasSizeAndRelease() async throws {
  let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
  let window = UIWindow(windowScene: scene)
  let previous = scene.keyWindow
  window.frame = CGRect(x: 0, y: 0, width: 360, height: 800)
  let host = UIHostingController(rootView: AnyView(ImageStudioView()))
  window.rootViewController = host
  window.makeKeyAndVisible()
  defer {
    window.isHidden = true
    window.rootViewController = nil
    previous?.makeKey()
  }
  func metal(_ view: UIView) -> MTKView? {
    if let v = view as? MTKView { return v }
    return view.subviews.lazy.compactMap { metal($0) }.first
  }
  for screen in [AnyView(ImageStudioView()), AnyView(ProductStudioView()), AnyView(CompositorStudioView())] {
    host.rootView = screen
    var canvas: MTKView?
    for _ in 0..<150 {
      host.view.layoutIfNeeded()
      canvas = metal(host.view)
      if let canvas, canvas.drawableSize.width > 100 { break }
      try await Task.sleep(for: .milliseconds(20))
    }
    let v = try #require(canvas)
    #expect(v.bounds.width > 250 && v.bounds.height > 200)
    let target = try #require(
      EffectQuality.balanced.resolution(
        width: v.bounds.width * scene.screen.nativeScale,
        height: v.bounds.height * scene.screen.nativeScale))
    #expect(v.drawableSize == CGSize(width: target.width, height: target.height))
  }
  let image = try #require(RasterImage(width: 1, height: 1, rgba: Data([80, 120, 160, 255])))
  var reports: [EffectStatistics] = []
  func surface(_ exposure: Float, _ quality: EffectQuality) -> AnyView {
    AnyView(
      MetalPreviewSurface(
        content: .image(image, .init(exposure: exposure), .init(), comparison: 0), quality: quality,
        onGesture: { _ in },
        onEvent: { event in
          if case .statistics(let s) = event { reports.append(s) }
          if case .failed = event { Issue.record("Preview initialization failed") }
        }, onUnexpectedError: { Issue.record("\($0)") }
      ).frame(width: 300, height: 250))
  }
  host.rootView = surface(0, .balanced)
  for _ in 0..<150 {
    if !reports.isEmpty { break }
    try await Task.sleep(for: .milliseconds(20))
  }
  let first = try #require(reports.last)
  try await Task.sleep(for: .milliseconds(150))
  #expect(reports.last?.submittedFrames == first.submittedFrames)
  host.rootView = surface(1, .economy)
  for _ in 0..<150 {
    if reports.count > 1 { break }
    try await Task.sleep(for: .milliseconds(20))
  }
  let target = try #require(
    EffectQuality.economy.resolution(
      width: 300 * scene.screen.nativeScale, height: 250 * scene.screen.nativeScale))
  #expect(reports.last?.width == target.width && reports.last?.height == target.height)
  let active = try #require(metal(host.view))
  host.rootView = AnyView(EmptyView())
  for _ in 0..<100 {
    if active.delegate == nil { break }
    try await Task.sleep(for: .milliseconds(20))
  }
  #expect(active.delegate == nil && active.isPaused)
  let disposedCount = reports.count
  try await Task.sleep(for: .milliseconds(150))
  #expect(reports.count == disposedCount)
}
