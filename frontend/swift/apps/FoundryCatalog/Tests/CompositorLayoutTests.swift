import FoundryGraphics
import MetalKit
import SwiftUI
import Testing

@MainActor @Test func compositorProfilesReuseTargetsResizeAndStopAfterRemoval() async throws {
  let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
  let previous = scene.keyWindow
  let window = UIWindow(windowScene: scene)
  window.frame = CGRect(x: 0, y: 0, width: 360, height: 800)
  let host = UIHostingController(rootView: AnyView(EmptyView()))
  window.rootViewController = host
  window.makeKeyAndVisible()
  defer {
    window.isHidden = true
    window.rootViewController = nil
    previous?.makeKey()
  }
  let base = try #require(RasterImage(width: 1, height: 1, rgba: Data([128, 128, 128, 255])))
  let overlay = try #require(AlphaImage(width: 1, height: 1, rgba: Data([255, 0, 0, 128])))
  var profiles: [GraphicsProfile] = []
  func surface(
    _ settings: CompositeSettings, _ quality: EffectQuality = .balanced, running: Bool = false
  ) -> AnyView {
    AnyView(
      MetalPreviewSurface(
        content: .composite(base, overlay, settings), quality: quality, running: running,
        onGesture: { _ in },
        onEvent: { if case .failed = $0 { Issue.record("Compositor failed") } },
        onUnexpectedError: { Issue.record("\($0)") }, onProfile: { profiles.append($0) }
      ).frame(width: 300, height: 250))
  }
  func wait(_ condition: () -> Bool) async throws {
    for _ in 0..<200 {
      host.view.layoutIfNeeded()
      if condition() { return }
      try await Task.sleep(for: .milliseconds(20))
    }
    Issue.record("Compositor callback timed out")
  }
  func metal(_ v: UIView) -> MTKView? {
    if let v = v as? MTKView { return v }
    return v.subviews.lazy.compactMap { metal($0) }.first
  }
  host.rootView = surface(.init())
  try await wait { !profiles.isEmpty }
  let first = try #require(profiles.last)
  #expect(first.passes == 4 && first.uploads == 2 && first.targetAllocations == 3)
  #expect(
    first.inputTextureBytes == 8
      && first.offscreenTextureBytes == first.width * first.height * 4 * 3)
  #expect(first.cpuEncodeMilliseconds >= 0)
  if let gpu = first.gpuMilliseconds { #expect(gpu > 0) }
  let initialCanvas = try #require(metal(host.view))
  try await Task.sleep(for: .milliseconds(150))
  #expect(profiles.last?.frame == first.frame)
  host.rootView = surface(.init(blur: 12, glow: 1, blend: .screen))
  try await wait { profiles.last?.frame ?? 0 > first.frame }
  #expect(metal(host.view) === initialCanvas)
  #expect(profiles.last?.uploads == 2 && profiles.last?.targetAllocations == 3)
  host.rootView = surface(.init(), .economy)
  try await wait { profiles.last?.width != first.width }
  #expect(profiles.last?.uploads == 2 && profiles.last?.targetAllocations == 6)
  let beforeRunning = try #require(profiles.last).frame
  host.rootView = surface(.init(), .economy, running: true)
  try await wait { profiles.last?.frame ?? 0 > beforeRunning + 5 }
  host.rootView = surface(.init(), .economy)
  try await wait { initialCanvas.isPaused }
  try await Task.sleep(for: .milliseconds(100))  // Let an already submitted buffer finish.
  let paused = profiles.count
  try await Task.sleep(for: .milliseconds(200))
  #expect(profiles.count == paused)
  host.rootView = AnyView(EmptyView())
  try await wait { initialCanvas.delegate == nil }
  let removed = profiles.count
  try await Task.sleep(for: .milliseconds(200))
  #expect(initialCanvas.isPaused && profiles.count == removed)
}
