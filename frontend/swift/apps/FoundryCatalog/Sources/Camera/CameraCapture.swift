@preconcurrency import AVFoundation
import FoundryGraphics
import FoundryKernel
import Observation
import OSLog
import SwiftUI

private enum CameraEvent: Sendable {
    case ready(canFlip: Bool, device: AVCaptureDevice)
    case failed(Failure)
}

/// AVFoundation mutations and capture completion are confined to one serial queue.
/// The session is exposed only for the native preview layer's documented attachment.
private final class CameraCaptureEngine: NSObject, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "dev.mobilefoundry.camera")
    private let output = AVCapturePhotoOutput()
    private let report: @MainActor @Sendable (UUID, CameraEvent) -> Void
    private var generation: UUID?
    private var pending: (id: Int64, completion: @MainActor @Sendable (AppResult<RasterImage>) -> Void)?
    private var observers: [NSObjectProtocol] = []
    private var rotation: AVCaptureDevice.RotationCoordinator?

    init(report: @escaping @MainActor @Sendable (UUID, CameraEvent) -> Void) {
        self.report = report
        super.init()
        for name in [AVCaptureSession.wasInterruptedNotification, AVCaptureSession.runtimeErrorNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: session, queue: nil) { [weak self] notification in
                if let error = notification.userInfo?[AVCaptureSessionErrorKey] as? any Error {
                    Logger(subsystem: "dev.mobilefoundry.catalog", category: "camera")
                        .error("Camera runtime error: \(String(reflecting: error), privacy: .private)")
                }
                guard let self else { return }
                self.queue.async { [self] in
                    guard let generation else { return }
                    pending = nil
                    if session.isRunning { session.stopRunning() }
                    emit(.failed(.unavailable(.init(message: "Camera interrupted. Enable it again to retry.", code: "camera.interrupted"))), generation: generation)
                }
            })
        }
    }
    deinit { observers.forEach(NotificationCenter.default.removeObserver) }

    private func emit(_ event: CameraEvent, generation: UUID) {
        Task { @MainActor [report] in report(generation, event) }
    }
    func start(front: Bool, generation: UUID) {
        queue.async { [self] in
            self.generation = generation
            if session.isRunning { session.stopRunning() }
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video,
                position: front ? .front : .back) else {
                emit(.failed(.unavailable(.init(message: "This camera is unavailable. Choose a photo from your library.", code: "camera.unavailable"))), generation: generation)
                return
            }
            session.beginConfiguration()
            session.inputs.forEach(session.removeInput)
            if session.canSetSessionPreset(.photo) { session.sessionPreset = .photo }
            do {
                let input = try AVCaptureDeviceInput(device: device)
                guard session.canAddInput(input) else {
                    session.commitConfiguration()
                    emit(.failed(.unavailable(.init(message: "Camera could not be started.", code: "camera.configuration"))), generation: generation)
                    return
                }
                session.addInput(input)
                rotation = AVCaptureDevice.RotationCoordinator(device: device, previewLayer: nil)
                if !session.outputs.contains(output) {
                    guard session.canAddOutput(output) else {
                        session.commitConfiguration()
                        emit(.failed(.unavailable(.init(message: "Photo capture is unavailable.", code: "camera.output"))), generation: generation)
                        return
                    }
                    session.addOutput(output)
                }
                session.commitConfiguration()
                session.startRunning()
                if session.isRunning {
                    let opposite = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video,
                        position: front ? .back : .front)
                    emit(.ready(canFlip: opposite != nil, device: device), generation: generation)
                } else {
                    emit(.failed(.unavailable(.init(message: "Camera could not be started. Try again.", code: "camera.start"))), generation: generation)
                }
            } catch {
                session.commitConfiguration()
                Logger(subsystem: "dev.mobilefoundry.catalog", category: "camera")
                    .error("Camera configuration: \(String(reflecting: error), privacy: .private)")
                emit(.failed(.unavailable(.init(message: "Camera could not be started. Try again.", code: "camera.configuration"))), generation: generation)
            }
        }
    }
    func stop() {
        queue.async { [self] in
            generation = nil
            pending = nil
            if session.isRunning { session.stopRunning() }
        }
    }
    func capture(completion: @escaping @MainActor @Sendable (AppResult<RasterImage>) -> Void) {
        queue.async { [self] in
            guard session.isRunning, pending == nil else {
                Task { @MainActor in completion(.failure(.unavailable(.init(message: "Camera is not ready.", code: "camera.not-ready")))) }
                return
            }
            let settings = AVCapturePhotoSettings()
            if let angle = rotation?.videoRotationAngleForHorizonLevelCapture,
               let connection = output.connection(with: .video), connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
            settings.flashMode = .off
            pending = (settings.uniqueID, completion)
            output.capturePhoto(with: settings, delegate: self)
        }
    }
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: (any Error)?) {
        let id = photo.resolvedSettings.uniqueID
        let data = photo.fileDataRepresentation()
        if let error {
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "camera")
                .error("Photo capture: \(String(reflecting: error), privacy: .private)")
        }
        let failed = error != nil
        queue.async { [self] in
            guard pending?.id == id, let completion = pending?.completion else { return }
            pending = nil
            let result = !failed ? data.map(PhotoDecoder.decode) ?? .failure(PhotoDecoder.invalid)
                : .failure(.unavailable(.init(message: "Photo capture failed. Please try again.", code: "camera.capture")))
            Task { @MainActor in completion(result) }
        }
    }
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishCaptureFor resolvedSettings: AVCaptureResolvedPhotoSettings, error: (any Error)?) {
        guard let error else { return }
        Logger(subsystem: "dev.mobilefoundry.catalog", category: "camera")
            .error("Photo completion: \(String(reflecting: error), privacy: .private)")
        let id = resolvedSettings.uniqueID
        queue.async { [self] in
            guard pending?.id == id, let completion = pending?.completion else { return }
            pending = nil
            Task { @MainActor in completion(.failure(.unavailable(.init(message: "Photo capture failed. Please try again.", code: "camera.capture")))) }
        }
    }
}

@MainActor @Observable
final class CameraController {
    var message = "Enable the camera to take a photo."
    var ready = false
    var capturing = false
    var denied = false
    var canFlip = false
    private(set) var device: AVCaptureDevice?
    private var front = false
    private var active = false
    private var generation = UUID()
    @ObservationIgnored private lazy var engine = CameraCaptureEngine { [weak self] generation, event in
        guard let self, self.active, self.generation == generation else { return }
        switch event {
        case .ready(let canFlip, let device): self.ready = true; self.canFlip = canFlip; self.device = device; self.message = "Capture, then apply filters and edits."
        case .failed(let failure): self.ready = false; self.capturing = false; self.message = failure.publicInfo().meta.message
        }
    }
    var session: AVCaptureSession { engine.session }

    func start() async {
        active = true
        generation = UUID()
        let current = generation
        ready = false
        denied = false
        #if targetEnvironment(simulator)
        message = "Camera capture needs a device. Choose a photo from your library."
        return
        #else
        let permission = AVCaptureDevice.authorizationStatus(for: .video)
        var granted = permission == .authorized
        if permission == .notDetermined { granted = await AVCaptureDevice.requestAccess(for: .video) }
        guard !Task.isCancelled, active, current == generation else { return }
        guard granted else { denied = true; message = "Camera access is off. Enable it in Settings or choose a photo."; return }
        message = "Starting camera…"
        engine.start(front: front, generation: current)
        #endif
    }
    func stop() { active = false; generation = UUID(); ready = false; capturing = false; engine.stop() }
    func flip() {
        guard ready, !capturing, canFlip else { return }
        front.toggle(); ready = false; generation = UUID(); engine.start(front: front, generation: generation)
    }
    func takePhoto(onPhoto: @escaping @MainActor (RasterImage) -> Void) {
        guard ready, !capturing else { return }
        capturing = true
        let current = generation
        engine.capture { [weak self] result in
            guard let self, self.active, self.generation == current else { return }
            self.capturing = false
            switch result {
            case .success(let photo): onPhoto(photo)
            case .failure(let failure): self.message = failure.publicInfo().meta.message
            }
        }
    }
}

private final class CameraPreviewUIView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    private var rotation: AVCaptureDevice.RotationCoordinator?
    private var observation: NSKeyValueObservation?
    func attach(session: AVCaptureSession, device: AVCaptureDevice?) {
        if preview.session !== session { preview.session = session }
        guard let device, rotation?.device !== device else { return }
        let coordinator = AVCaptureDevice.RotationCoordinator(device: device, previewLayer: preview)
        rotation = coordinator
        observation = coordinator.observe(\.videoRotationAngleForHorizonLevelPreview, options: [.initial, .new]) { [weak self] coordinator, _ in
            let angle = coordinator.videoRotationAngleForHorizonLevelPreview
            Task { @MainActor [weak self] in
                guard let connection = self?.preview.connection, connection.isVideoRotationAngleSupported(angle) else { return }
                connection.videoRotationAngle = angle
            }
        }
    }
    func disconnect() { observation = nil; rotation = nil; preview.session = nil }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    let device: AVCaptureDevice?
    func makeUIView(context: Context) -> UIView {
        let view = CameraPreviewUIView()
        view.preview.videoGravity = .resizeAspectFill
        view.attach(session: session, device: device)
        return view
    }
    func updateUIView(_ uiView: UIView, context: Context) {
        (uiView as? CameraPreviewUIView)?.attach(session: session, device: device)
    }
    static func dismantleUIView(_ uiView: UIView, coordinator: ()) {
        (uiView as? CameraPreviewUIView)?.disconnect()
    }
}
