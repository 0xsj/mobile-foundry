#if os(iOS)
  import FoundryKernel
  import MetalKit
  import SwiftUI

  /// Native attachment shared by image and mesh previews. GPU assets stay private.
  public struct MetalPreviewSurface: UIViewRepresentable {
    public let content: PreviewContent
    public let quality: EffectQuality
    public let running: Bool
    public let onGesture: (CanvasGesture) -> Void
    public let onEvent: (EffectEvent) -> Void
    public let onUnexpectedError: (any Error) -> Void
    public init(
      content: PreviewContent, quality: EffectQuality = .balanced, running: Bool = false,
      onGesture: @escaping (CanvasGesture) -> Void, onEvent: @escaping (EffectEvent) -> Void,
      onUnexpectedError: @escaping (any Error) -> Void
    ) {
      self.content = content
      self.quality = quality
      self.running = running
      self.onGesture = onGesture
      self.onEvent = onEvent
      self.onUnexpectedError = onUnexpectedError
    }
    public func makeCoordinator() -> Coordinator { Coordinator() }
    public func sizeThatFits(_ proposal: ProposedViewSize, uiView: MTKView, context: Context)
      -> CGSize?
    {
      guard let w = proposal.width, let h = proposal.height else { return nil }
      return .init(width: w, height: h)
    }
    public func makeUIView(context: Context) -> MTKView {
      let view = PreviewMetalView(frame: .zero, device: MTLCreateSystemDefaultDevice())
      view.isPaused = true
      view.enableSetNeedsDisplay = true
      view.autoResizeDrawable = false
      view.colorPixelFormat = .bgra8Unorm
      view.depthStencilPixelFormat = .depth32Float
      view.clearDepth = 1
      view.isAccessibilityElement = true
      view.accessibilityLabel = "Interactive preview canvas"
      view.accessibilityHint = "Drag and pinch, or use the controls below."
      view.onGesture = onGesture
      let owner = context.coordinator
      owner.onEvent = onEvent
      owner.onUnexpectedError = onUnexpectedError
      guard let device = view.device else {
        owner.publish(
          .failed(
            .unavailable(
              .init(
                message: "GPU rendering is unavailable on this device.",
                code: "graphics.unavailable"))))
        return view
      }
      do {
        let renderer = try PreviewMetalRenderer(device: device, owner: owner)
        owner.renderer = renderer
        view.delegate = renderer
        view.onLayout = { [weak renderer] view in renderer?.size(view) }
        owner.publish(.ready("Metal · \(device.name)"))
      } catch { owner.fail(error) }
      return view
    }
    public func updateUIView(_ view: MTKView, context: Context) {
      context.coordinator.onEvent = onEvent
      context.coordinator.onUnexpectedError = onUnexpectedError
      (view as? PreviewMetalView)?.onGesture = onGesture
      context.coordinator.renderer?.update(
        view, content: content, quality: quality, running: running)
    }
    public static func dismantleUIView(_ view: MTKView, coordinator: Coordinator) {
      coordinator.disposed = true
      view.isPaused = true
      view.delegate = nil
      (view as? PreviewMetalView)?.onLayout = { _ in }
      (view as? PreviewMetalView)?.onGesture = { _ in }
      view.releaseDrawables()
      coordinator.renderer = nil
    }
    @MainActor public final class Coordinator {
      fileprivate var renderer: PreviewMetalRenderer?
      fileprivate var disposed = false
      fileprivate var onEvent: (EffectEvent) -> Void = { _ in }
      fileprivate var onUnexpectedError: (any Error) -> Void = { _ in }
      fileprivate func publish(_ event: EffectEvent) {
        Task { @MainActor [weak self] in
          guard let self, !disposed else { return }
          onEvent(event)
        }
      }
      fileprivate func fail(_ error: any Error) {
        Task { @MainActor [weak self] in
          guard let self, !disposed else { return }
          if case PreviewRenderError.resourceUnavailable = error {
            onEvent(
              .failed(
                .unavailable(
                  .init(
                    message: "GPU resources are unavailable for this asset.",
                    code: "graphics.asset-capability"))))
          } else {
            onUnexpectedError(error)
            onEvent(
              .failed(
                .internalError(
                  .init(message: "GPU initialization failed.", code: "graphics.pipeline"))))
          }
        }
      }
    }
  }

  @MainActor private final class PreviewMetalView: MTKView, UIGestureRecognizerDelegate {
    var onGesture: (CanvasGesture) -> Void = { _ in }
    var onLayout: (MTKView) -> Void = { _ in }
    override init(frame: CGRect, device: (any MTLDevice)?) {
      super.init(frame: frame, device: device)
      let pan = UIPanGestureRecognizer(target: self, action: #selector(drag(_:)))
      pan.maximumNumberOfTouches = 1
      pan.delegate = self
      addGestureRecognizer(pan)
      let pinch = UIPinchGestureRecognizer(target: self, action: #selector(zoom(_:)))
      pinch.delegate = self
      addGestureRecognizer(pinch)
    }
    required init(coder: NSCoder) { fatalError("Use the programmatic preview surface") }
    override func layoutSubviews() {
      super.layoutSubviews()
      onLayout(self)
      if isPaused { setNeedsDisplay() }
    }
    @objc private func drag(_ recognizer: UIPanGestureRecognizer) {
      guard bounds.width > 0, bounds.height > 0,
        recognizer.state == .began || recognizer.state == .changed
      else { return }
      let point = recognizer.location(in: self)
      let delta = recognizer.translation(in: self)
      onGesture(
        .drag(
          x: Float(point.x / bounds.width), y: Float(point.y / bounds.height),
          dx: Float(delta.x / bounds.width), dy: Float(delta.y / bounds.height)))
      recognizer.setTranslation(.zero, in: self)
    }
    @objc private func zoom(_ recognizer: UIPinchGestureRecognizer) {
      guard recognizer.state == .changed else { return }
      onGesture(.zoom(Float(recognizer.scale)))
      recognizer.scale = 1
    }
    func gestureRecognizer(
      _ gestureRecognizer: UIGestureRecognizer,
      shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool { other.view === self }
  }

  @MainActor private final class PreviewMetalRenderer: NSObject, MTKViewDelegate {
    private let pipeline: PreviewPipeline
    private let queue: any MTLCommandQueue
    private weak var owner: MetalPreviewSurface.Coordinator?
    private var content: PreviewContent?
    private var quality = EffectQuality.balanced
    private var running = false
    private var clock = EffectClock()
    private var frames = 0, reported = 0
    private var reportTime = CACurrentMediaTime()
    private var failed = false
    init(device: any MTLDevice, owner: MetalPreviewSurface.Coordinator) throws {
      pipeline = try PreviewPipeline(device: device)
      guard let queue = device.makeCommandQueue() else {
        throw PreviewRenderError.resourceUnavailable
      }
      self.queue = queue
      self.owner = owner
    }
    func update(_ view: MTKView, content: PreviewContent, quality: EffectQuality, running: Bool) {
      guard self.content != content || self.quality != quality || self.running != running else {
        return
      }
      if running != self.running {
        clock.suspend()
        reportTime = CACurrentMediaTime()
        reported = frames
      }
      self.content = content
      self.quality = quality
      self.running = running
      do { try pipeline.prepare(content) } catch {
        failed = true
        owner?.fail(error)
        return
      }
      view.preferredFramesPerSecond = quality.framesPerSecond
      view.isPaused = !running
      view.enableSetNeedsDisplay = !running
      size(view)
      if !running { view.setNeedsDisplay() }
    }
    func size(_ view: MTKView) {
      let scale = view.window?.screen.nativeScale ?? view.traitCollection.displayScale
      guard
        let target = quality.resolution(
          width: view.bounds.width * scale, height: view.bounds.height * scale)
      else { return }
      let next = CGSize(width: target.width, height: target.height)
      if view.drawableSize != next { view.drawableSize = next }
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
      guard !failed, let owner, !owner.disposed, let content, view.bounds.width > 0,
        view.bounds.height > 0,
        let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
        let command = queue.makeCommandBuffer(),
        let encoder = command.makeRenderCommandEncoder(descriptor: pass)
      else { return }
      let now = CACurrentMediaTime()
      pipeline.encode(
        encoder, width: drawable.texture.width, height: drawable.texture.height,
        time: clock.frame(at: now, running: running), content: content)
      encoder.endEncoding()
      command.present(drawable)
      command.commit()
      frames += 1
      if !running || now - reportTime >= 1 {
        owner.publish(
          .statistics(
            .init(
              submittedFrames: frames,
              submissionsPerSecond: running
                ? Double(frames - reported) / max(0.001, now - reportTime) : 0,
              width: drawable.texture.width, height: drawable.texture.height)))
        reported = frames
        reportTime = now
      }
    }
  }
#endif
