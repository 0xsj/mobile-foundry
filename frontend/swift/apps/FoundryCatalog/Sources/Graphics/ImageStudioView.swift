import FoundryGraphics
import FoundryKernel
import FoundryUI
import OSLog
import SwiftUI

struct ImageStudioView: View {
  @Environment(\.foundry) private var tokens
  @State private var image: RasterImage?
  @State private var failure: Failure?
  @State private var exposure = 0.35
  @State private var saturation = 1.15
  @State private var vignette = 0.25
  @State private var comparison = 0.5
  @State private var viewport = ImageViewport()
  @State private var move = false
  @State private var quality = EffectQuality.balanced
  @State private var status = "Loading photograph…"
  @State private var attempt = 0
  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: tokens.space.section) {
        Text(
          "Compare an original photograph with live adjustments. Pinch to zoom; choose Move image to pan."
        ).foregroundStyle(tokens.colors.inkSecondary.color)
        if let failure {
          Text(failure.publicInfo().meta.message)
          Button("Try again") {
            self.failure = nil
            image = nil
            attempt += 1
          }
        } else if let image {
          MetalPreviewSurface(
            content: .image(
              image,
              .init(
                exposure: Float(exposure), saturation: Float(saturation), vignette: Float(vignette)),
              viewport, comparison: Float(comparison)), quality: quality, onGesture: gesture,
            onEvent: receive, onUnexpectedError: diagnostic
          )
          .frame(maxWidth: .infinity).aspectRatio(1.2, contentMode: .fit).id(attempt)
        } else {
          ProgressView("Loading photograph…").frame(maxWidth: .infinity, minHeight: 240)
        }
        FoundrySurface {
          VStack(alignment: .leading, spacing: tokens.space.stack) {
            Picker("Canvas tool", selection: $move) {
              Text("Compare").tag(false)
              Text("Move image").tag(true)
            }.pickerStyle(.segmented)
            Text(move ? "Drag to pan the image." : "Drag to move the before/after divider.").font(
              tokens.typography.caption)
            Text("Exposure")
            Slider(value: $exposure, in: -2...2).accessibilityLabel("Exposure")
            Text("Saturation")
            Slider(value: $saturation, in: 0...2).accessibilityLabel("Saturation")
            Text("Vignette")
            Slider(value: $vignette, in: 0...1).accessibilityLabel("Vignette")
            Text("Comparison")
            Slider(value: $comparison, in: 0...1).accessibilityLabel("Comparison")
            HStack(spacing: tokens.space.stack) {
              Button("Original") { comparison = 1 }
              Button("Edited") { comparison = 0 }
            }.buttonStyle(.bordered)
            HStack(spacing: tokens.space.stack) {
              Button("Zoom out") { viewport = viewport.scaled(by: 1 / 1.25) }
              Button("Zoom in") { viewport = viewport.scaled(by: 1.25) }
            }.buttonStyle(.bordered)
            Text("Zoom: \(viewport.zoom,specifier:"%.2f")×")
            Picker("Quality", selection: $quality) {
              Text("Economy").tag(EffectQuality.economy)
              Text("Balanced").tag(EffectQuality.balanced)
            }.pickerStyle(.segmented)
            Button("Reset edits") {
              exposure = 0
              saturation = 1
              vignette = 0
              comparison = 0.5
              viewport = .init()
            }
          }.padding(tokens.space.page)
        }
        Text("Original on the left · Edited on the right").font(tokens.typography.caption)
        Text(status).font(tokens.typography.caption)
      }.padding(tokens.space.page)
    }.background(tokens.colors.surfaceGround.color).navigationTitle("Image studio")
      .navigationBarTitleDisplayMode(.inline)
      .task(id: attempt) {
        let result = await Task.detached(priority: .userInitiated) { PreviewAssets.image() }.value
        guard !Task.isCancelled else { return }
        switch result {
        case .success(let value): image = value
        case .failure(let value): failure = value
        }
      }
  }
  private func gesture(_ value: CanvasGesture) {
    switch value {
    case .drag(let x, _, let dx, let dy):
      if move {
        viewport = viewport.moved(dx: dx, dy: dy)
      } else {
        comparison = Double(min(1, max(0, x)))
      }
    case .zoom(let scale): viewport = viewport.scaled(by: scale)
    }
  }
  private func receive(_ event: EffectEvent) {
    switch event {
    case .ready(let backend): status = backend
    case .statistics(let s):
      status = "Static preview · \(s.width) × \(s.height) · Submitted: \(s.submittedFrames)"
    case .failed(let value): failure = value
    }
  }
  private func diagnostic(_ error: any Error) {
    Logger(subsystem: "dev.mobilefoundry.catalog", category: "graphics").error(
      "Preview error: \(String(reflecting:error),privacy:.private)")
  }
}
