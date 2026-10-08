#if os(iOS)
import FoundryKernel
import MetalKit
import SwiftUI

/// Stable native canvas. Inputs are snapshots; GPU handles stay inside the adapter.
public struct MetalEffectSurface: UIViewRepresentable {
    public let settings: EffectSettings
    public let running: Bool
    public let onPoint: (EffectPoint) -> Void
    public let onEvent: (EffectEvent) -> Void
    public let onUnexpectedError: (any Error) -> Void

    public init(settings: EffectSettings, running: Bool, onPoint: @escaping (EffectPoint) -> Void,
                onEvent: @escaping (EffectEvent) -> Void, onUnexpectedError: @escaping (any Error) -> Void) {
        self.settings = settings; self.running = running; self.onPoint = onPoint
        self.onEvent = onEvent; self.onUnexpectedError = onUnexpectedError
    }
    public func makeCoordinator() -> Coordinator { Coordinator() }
    public func sizeThatFits(_ proposal: ProposedViewSize, uiView: MTKView, context: Context) -> CGSize? {
        // MTKView's drawable-derived ideal size must not determine its SwiftUI bounds.
        guard let width = proposal.width, let height = proposal.height else { return nil }
        return CGSize(width: width, height: height)
    }
    public func makeUIView(context: Context) -> MTKView {
        let view = TouchMetalView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.isPaused = true; view.enableSetNeedsDisplay = true; view.autoResizeDrawable = false
        view.colorPixelFormat = .bgra8Unorm; view.framebufferOnly = true
        view.isAccessibilityElement = true; view.accessibilityLabel = "Interactive GPU canvas"
        view.accessibilityHint = "Drag to steer the effect. Reset focus centers it."
        view.onPoint = onPoint
        let coordinator = context.coordinator
        coordinator.onEvent = onEvent
        guard let device = view.device else {
            coordinator.publish(.failed(.unavailable(.init(message: "GPU rendering is unavailable on this device.", code: "graphics.unavailable"))))
            return view
        }
        do {
            let renderer = try Renderer(device: device, coordinator: coordinator)
            coordinator.renderer = renderer; view.delegate = renderer
            view.onLayout = { [weak renderer] view in renderer?.size(view) }
            coordinator.publish(.ready("Metal · \(device.name)"))
        } catch {
            Task { @MainActor [weak coordinator] in
                guard let coordinator, !coordinator.disposed else { return }
                onUnexpectedError(error)
            }
            coordinator.publish(.failed(.internalError(.init(message: "GPU initialization failed.", code: "graphics.pipeline"))))
        }
        return view
    }
    public func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.onEvent = onEvent
        (view as? TouchMetalView)?.onPoint = onPoint
        context.coordinator.renderer?.update(view: view, settings: settings, running: running)
    }
    public static func dismantleUIView(_ view: MTKView, coordinator: Coordinator) {
        coordinator.disposed = true; view.isPaused = true; view.delegate = nil
        (view as? TouchMetalView)?.onLayout = { _ in }
        (view as? TouchMetalView)?.onPoint = { _ in }
        view.releaseDrawables(); coordinator.renderer = nil
    }

    @MainActor public final class Coordinator {
        fileprivate var renderer: Renderer?
        fileprivate var disposed = false
        fileprivate var onEvent: (EffectEvent) -> Void = { _ in }
        fileprivate func publish(_ event: EffectEvent) {
            Task { @MainActor [weak self] in guard let self, !disposed else { return }; onEvent(event) }
        }
    }
}

@MainActor private final class TouchMetalView: MTKView {
    var onPoint: (EffectPoint) -> Void = { _ in }
    var onLayout: (MTKView) -> Void = { _ in }
    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout(self)
        if isPaused { setNeedsDisplay() }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) { point(touches) }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { point(touches) }
    private func point(_ touches: Set<UITouch>) {
        guard let touch = touches.first, bounds.width > 0, bounds.height > 0 else { return }
        let location = touch.location(in: self)
        onPoint(.init(x: Float(location.x / bounds.width), y: Float(location.y / bounds.height)))
    }
}

@MainActor fileprivate final class Renderer: NSObject, MTKViewDelegate {
    private let pipeline: EffectPipeline
    private let queue: any MTLCommandQueue
    private weak var coordinator: MetalEffectSurface.Coordinator?
    private var settings = EffectSettings()
    private var running = false
    private var configured = false
    private var clock = EffectClock()
    private var frames = 0
    private var reportFrames = 0
    private var reportTime = CACurrentMediaTime()

    init(device: any MTLDevice, coordinator: MetalEffectSurface.Coordinator) throws {
        pipeline = try EffectPipeline(device: device)
        guard let queue = device.makeCommandQueue() else { throw RendererError.noQueue }
        self.queue = queue; self.coordinator = coordinator
    }
    func update(view: MTKView, settings: EffectSettings, running: Bool) {
        guard !configured || settings != self.settings || running != self.running else { return }
        if running != self.running { clock.suspend(); reportTime = CACurrentMediaTime(); reportFrames = frames }
        configured = true; self.settings = settings; self.running = running
        view.preferredFramesPerSecond = settings.quality.framesPerSecond
        view.enableSetNeedsDisplay = !running; view.isPaused = !running
        size(view)
        if !running { view.setNeedsDisplay() }
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    fileprivate func size(_ view: MTKView) {
        // The screen scale is independent of MTKView's manually reduced drawable.
        let scale = view.window?.screen.nativeScale ?? view.traitCollection.displayScale
        guard let target = settings.quality.resolution(width: view.bounds.width * scale,
                                                       height: view.bounds.height * scale) else { return }
        let size = CGSize(width: target.width, height: target.height)
        if view.drawableSize != size { view.drawableSize = size }
    }
    func draw(in view: MTKView) {
        guard let coordinator, !coordinator.disposed, view.bounds.width > 0, view.bounds.height > 0 else { return }
        guard let descriptor = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
              let command = queue.makeCommandBuffer(), let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else { return }
        let now = CACurrentMediaTime()
        pipeline.encode(encoder, width: drawable.texture.width, height: drawable.texture.height,
                        time: clock.frame(at: now, running: running), settings: settings)
        encoder.endEncoding(); command.present(drawable); command.commit(); frames += 1
        if !running || now - reportTime >= 1 {
            let rate = running ? Double(frames - reportFrames) / max(0.001, now - reportTime) : 0
            coordinator.publish(.statistics(.init(submittedFrames: frames, submissionsPerSecond: rate,
                                                  width: drawable.texture.width, height: drawable.texture.height)))
            reportFrames = frames; reportTime = now
        }
    }
    private enum RendererError: Error { case noQueue }
}
#endif
