import FoundryGraphics
import FoundryKernel
import FoundryUI
import OSLog
import SwiftUI

struct ProductStudioView: View {
  @Environment(\.foundry) private var tokens
  @Environment(\.scenePhase) private var phase
  @State private var mesh: PreviewMesh?
  @State private var failure: Failure?
  @State private var camera = OrbitCamera()
  @State private var finish = ProductFinish.porcelain
  @State private var quality = EffectQuality.balanced
  @State private var turntable = false
  @State private var reduced = false
  @State private var visible = false
  @State private var status = "Loading model…"
  @State private var attempt = 0
  private var running: Bool {
    turntable && !reduced && !tokens.motion.reduced && visible && phase == .active
  }
  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: tokens.space.section) {
        VStack(alignment: .leading, spacing: tokens.space.inline) {
          Text("Studio lamp").font(tokens.typography.heading)
          Text("Drag to orbit. Pinch to zoom. Explore three finishes on the same model.")
            .foregroundStyle(tokens.colors.inkSecondary.color)
        }
        if let failure {
          Text(failure.publicInfo().meta.message)
          Button("Try again") {
            self.failure = nil
            mesh = nil
            attempt += 1
          }
        } else if let mesh {
          MetalPreviewSurface(
            content: .product(mesh, camera, finish), quality: quality, running: running,
            onGesture: gesture, onEvent: receive, onUnexpectedError: diagnostic
          )
          .frame(maxWidth: .infinity).aspectRatio(1.2, contentMode: .fit).id(attempt)
        } else {
          ProgressView("Loading model…").frame(maxWidth: .infinity, minHeight: 240)
        }
        FoundrySurface {
          VStack(alignment: .leading, spacing: tokens.space.stack) {
            Picker("Finish", selection: $finish) {
              ForEach(ProductFinish.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
            }.pickerStyle(.segmented)
            Text("\(finish.rawValue.capitalized) finish").font(tokens.typography.label)
            HStack(spacing: tokens.space.stack) {
              Button("Rotate left") { camera = camera.rotated(dx: -0.08, dy: 0) }
              Button("Rotate right") { camera = camera.rotated(dx: 0.08, dy: 0) }
            }.buttonStyle(.bordered)
            HStack(spacing: tokens.space.stack) {
              Button("Zoom out") { camera = camera.scaled(by: 1 / 1.2) }
              Button("Zoom in") { camera = camera.scaled(by: 1.2) }
            }.buttonStyle(.bordered)
            Button("Reset camera") { camera = .init() }
            Text(
              "Camera: \(camera.yaw,specifier:"%.2f"), \(camera.pitch,specifier:"%.2f") · Distance: \(camera.distance,specifier:"%.2f")"
            ).font(tokens.typography.caption)
            Toggle("Turntable", isOn: $turntable)
            Toggle("Reduce motion preview", isOn: $reduced)
            Text(running ? "Turntable running" : "Turntable paused").font(tokens.typography.label)
            Picker("Quality", selection: $quality) {
              Text("Economy").tag(EffectQuality.economy)
              Text("Balanced").tag(EffectQuality.balanced)
            }.pickerStyle(.segmented)
          }.padding(tokens.space.page)
        }
        Text(status).font(tokens.typography.caption)
        Text("Studio lighting preview. Appearance is illustrative.").font(tokens.typography.caption)
      }.padding(tokens.space.page)
    }.background(tokens.colors.surfaceGround.color).navigationTitle("Product studio")
      .navigationBarTitleDisplayMode(.inline)
      .onAppear { visible = true }.onDisappear { visible = false }
      .task(id: attempt) {
        let result = await Task.detached(priority: .userInitiated) { PreviewAssets.mesh() }.value
        guard !Task.isCancelled else { return }
        switch result {
        case .success(let value): mesh = value
        case .failure(let value): failure = value
        }
      }
  }
  private func gesture(_ value: CanvasGesture) {
    switch value {
    case .drag(_, _, let dx, let dy): camera = camera.rotated(dx: dx, dy: dy)
    case .zoom(let scale): camera = camera.scaled(by: scale)
    }
  }
  private func receive(_ event: EffectEvent) {
    switch event {
    case .ready(let backend): status = backend
    case .statistics(let s):
      status = "Target: \(s.width) × \(s.height) · Submitted: \(s.submittedFrames)"
    case .failed(let value): failure = value
    }
  }
  private func diagnostic(_ error: any Error) {
    Logger(subsystem: "dev.mobilefoundry.catalog", category: "graphics").error(
      "Preview error: \(String(reflecting:error),privacy:.private)")
  }
}
