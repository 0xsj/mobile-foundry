import FoundryGraphics
import FoundryKernel
import FoundryUI
import PhotosUI
import OSLog
import SwiftUI

struct CameraView: View {
    @Environment(\.foundry) private var tokens
    @Environment(\.scenePhase) private var phase
    @Binding var photo: RasterImage?
    let isSelected: Bool
    @State private var camera = CameraController()
    @State private var enabled = false
    @State private var grid = true
    @State private var pickerPresented = false
    @State private var librarySelection: PhotosPickerItem?
    @State private var loadingPhoto = false
    @State private var importFailure: Failure?

    private var shouldRun: Bool {
        isSelected && phase == .active && enabled && photo == nil
            && !pickerPresented && librarySelection == nil && !loadingPhoto
    }
    var body: some View {
        Group {
            if isSelected, let photo {
                ImageEditorView(image: photo, title: "Edit photo", startsEdited: true)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Retake") { self.photo = nil }
                        }
                    }
            } else {
                VStack(spacing: tokens.space.stack) {
                    ZStack {
                        Color.black
                        if shouldRun { CameraPreview(session: camera.session, device: camera.device) }
                        if camera.ready && grid {
                            Canvas { context, size in
                                var path = Path()
                                for fraction in [1.0 / 3, 2.0 / 3] {
                                    path.move(to: .init(x: size.width * fraction, y: 0))
                                    path.addLine(to: .init(x: size.width * fraction, y: size.height))
                                    path.move(to: .init(x: 0, y: size.height * fraction))
                                    path.addLine(to: .init(x: size.width, y: size.height * fraction))
                                }
                                context.stroke(path, with: .color(.white.opacity(0.3)), lineWidth: 1)
                            }.allowsHitTesting(false).accessibilityHidden(true)
                        }
                        if !camera.ready {
                            VStack(spacing: tokens.space.stack) {
                                Image(systemName: "camera").font(.system(size: 44, weight: .light))
                                Text(camera.message).multilineTextAlignment(.center)
                                if camera.denied {
                                    Button("Open Settings") {
                                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                                    }
                                } else {
                                    Button("Enable camera") {
                                        if enabled { Task { await camera.start() } } else { enabled = true }
                                    }
                                        .disabled(!isSelected || phase != .active || loadingPhoto)
                                }
                            }.foregroundStyle(.white).padding(tokens.space.page)
                        }
                    }.clipShape(RoundedRectangle(cornerRadius: tokens.shape.panel))
                    Text(camera.ready ? camera.message : "PHOTO")
                        .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
                    if loadingPhoto { ProgressView("Opening photo…") }
                    if let importFailure { Text(importFailure.publicInfo().meta.message).font(tokens.typography.caption) }
                    HStack {
                        Button {
                            importFailure = nil
                            pickerPresented = true
                        } label: { Label("Photos", systemImage: "photo") }
                            .accessibilityLabel("Choose photo")
                            .disabled(loadingPhoto || camera.capturing)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button { camera.takePhoto { photo = $0 } } label: {
                            Circle().fill(tokens.colors.ink.color).frame(width: 60, height: 60)
                                .padding(5).overlay(Circle().stroke(tokens.colors.ink.color, lineWidth: 2))
                        }.accessibilityLabel("Take photo").disabled(!camera.ready || camera.capturing || loadingPhoto || pickerPresented)
                            .opacity(camera.ready && !camera.capturing && !loadingPhoto && !pickerPresented ? 1 : 0.35)
                        Button { camera.flip() } label: { Image(systemName: "arrow.triangle.2.circlepath.camera").frame(minWidth: 44, minHeight: 44) }
                            .accessibilityLabel("Flip camera").disabled(!camera.canFlip || !camera.ready || camera.capturing)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                    Toggle("Viewfinder grid", isOn: $grid).font(tokens.typography.caption)
                }.padding(tokens.space.page).navigationTitle("Camera").navigationBarTitleDisplayMode(.inline)
            }
        }
        .photosPicker(isPresented: $pickerPresented, selection: $librarySelection,
            matching: .images, preferredItemEncoding: .current)
        .task(id: shouldRun) {
            if shouldRun { await camera.start() } else { camera.stop() }
        }
        .task(id: librarySelection) {
            guard let item = librarySelection, isSelected else { return }
            loadingPhoto = true
            do {
                let result = try await PhotoLibraryImporter.load(item)
                guard !Task.isCancelled else { return }
                switch result {
                case .success(let value): photo = value
                case .failure(let failure): importFailure = failure
                }
                loadingPhoto = false
                librarySelection = nil
            } catch is CancellationError {
                // The next request or leaving the tab owns state cleanup.
            } catch {
                Logger(subsystem: "dev.mobilefoundry.catalog", category: "photos")
                    .error("Photo import boundary: \(String(reflecting: error), privacy: .private)")
                loadingPhoto = false
                librarySelection = nil
                importFailure = .internalError(.init(message: "This photo could not be opened."))
            }
        }
        .onChange(of: isSelected) { _, selected in
            if !selected { librarySelection = nil; loadingPhoto = false; pickerPresented = false }
        }
        .onDisappear { camera.stop() }
    }
}
