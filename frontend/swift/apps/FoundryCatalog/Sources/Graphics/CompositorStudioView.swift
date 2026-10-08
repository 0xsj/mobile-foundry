import FoundryGraphics
import FoundryKernel
import FoundryUI
import OSLog
import SwiftUI

struct CompositorStudioView: View {
  @Environment(\.foundry) private var tokens
  @Environment(\.scenePhase) private var phase
  @State private var image: RasterImage?
  @State private var overlay = PreviewAssets.overlay()
  @State private var failure: Failure?
  @State private var opacity = 0.85
  @State private var scale = 0.65
  @State private var radius = 0.32
  @State private var feather = 0.12
  @State private var blur = 0.0
  @State private var glow = 0.35
  @State private var comparison = 0.5
  @State private var layerX = 0.5
  @State private var layerY = 0.5
  @State private var maskX = 0.5
  @State private var maskY = 0.5
  @State private var maskEnabled = true
  @State private var profiling = false
  @State private var reduced = false
  @State private var visible = false
  @State private var tool = Tool.mask
  @State private var blend = CompositeBlend.normal
  @State private var quality = EffectQuality.balanced
  @State private var profile: GraphicsProfile?
  @State private var attempt = 0
  private enum Tool: String, CaseIterable { case layer, mask, compare }
  private var running: Bool {
    profiling && !reduced && !tokens.motion.reduced && visible && phase == .active
  }
  private var settings: CompositeSettings {
    .init(
      opacity: Float(opacity), scale: Float(scale),
      layerCenter: .init(x: Float(layerX), y: Float(layerY)),
      maskCenter: .init(x: Float(maskX), y: Float(maskY)), radius: Float(radius),
      feather: Float(feather),
      blur: Float(blur), glow: Float(glow), comparison: Float(comparison), maskEnabled: maskEnabled,
      blend: blend)
  }
  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: tokens.space.section) {
        Text(
          "Layer a transparent graphic over a photograph. Drag with the selected tool; pinch to scale the layer."
        )
        .foregroundStyle(tokens.colors.inkSecondary.color)
        if let failure {
          Text(failure.publicInfo().meta.message)
          Button("Try again") {
            self.failure = nil
            image = nil
            profile = nil
            attempt += 1
          }
        } else if let image {
          MetalPreviewSurface(
            content: .composite(image, overlay, settings), quality: quality, running: running,
            onGesture: gesture, onEvent: { if case .failed(let value) = $0 { failure = value } },
            onUnexpectedError: { error in
              Logger(subsystem: "dev.mobilefoundry.catalog", category: "graphics").error(
                "Compositor error: \(String(reflecting: error), privacy: .private)")
            }, onProfile: { profile = $0 }
          )
          .frame(maxWidth: .infinity).aspectRatio(1.2, contentMode: .fit).id(attempt)
        } else {
          ProgressView("Loading photograph…").frame(maxWidth: .infinity, minHeight: 240)
        }
        Text("Original on the left · Composed on the right").font(tokens.typography.caption)
        Surface {
          VStack(alignment: .leading, spacing: tokens.space.stack) {
            Picker("Canvas tool", selection: $tool) {
              ForEach(Tool.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
            }.pickerStyle(.segmented)
            Text(
              "Drag to \(tool == .layer ? "move the layer" : tool == .mask ? "move the mask" : "compare the composition")."
            ).font(tokens.typography.caption)
            Picker("Blend", selection: $blend) {
              ForEach(CompositeBlend.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
            }.pickerStyle(.segmented)
            control("Opacity", $opacity, 0...1)
            control("Layer scale", $scale, 0.15...1)
            control("Layer X", $layerX, 0...1)
            control("Layer Y", $layerY, 0...1)
            Toggle("Feathered mask", isOn: $maskEnabled)
            Group {
              control("Mask radius", $radius, 0.05...0.75)
              control("Mask feather", $feather, 0...0.3)
              control("Mask X", $maskX, 0...1)
              control("Mask Y", $maskY, 0...1)
            }.disabled(!maskEnabled)
            control("Blur · target pixels", $blur, 0...24)
            control("Glow", $glow, 0...1)
            control("Comparison", $comparison, 0...1)
            HStack {
              Button("Original") { comparison = 1 }
              Button("Composed") { comparison = 0 }
            }.buttonStyle(.bordered)
            Button("Reset composition", action: reset)
          }.padding(tokens.space.page)
        }
        Surface {
          VStack(alignment: .leading, spacing: tokens.space.stack) {
            Text("Render workload").font(tokens.typography.heading)
            Picker("Quality", selection: $quality) {
              Text("Economy").tag(EffectQuality.economy)
              Text("Balanced").tag(EffectQuality.balanced)
            }.pickerStyle(.segmented)
            Toggle("Profile redraws", isOn: $profiling)
            Toggle("Reduce motion preview", isOn: $reduced)
            Text(running ? "Redraw workload running" : "Draw on change").font(
              tokens.typography.label)
            if let profile {
              Text(
                "\(profile.width) × \(profile.height) · \(profile.passes) passes · Frame \(profile.frame)"
              )
              Text("Uploads: \(profile.uploads) · Target allocations: \(profile.targetAllocations)")
              Text(
                "Texture payload · Inputs: \(Double(profile.inputTextureBytes) / 1_048_576, specifier: "%.2f") MiB · Targets: \(Double(profile.offscreenTextureBytes) / 1_048_576, specifier: "%.2f") MiB"
              )
              Text("CPU encode: \(profile.cpuEncodeMilliseconds, specifier: "%.2f") ms")
              if let milliseconds = profile.gpuMilliseconds {
                Text("GPU: \(milliseconds, specifier: "%.2f") ms · \(profile.gpuTiming)")
              } else {
                Text(profile.gpuTiming)
              }
            }
            Text(
              "Payload estimates exclude driver, depth and display buffers. Profile sustained workloads on a physical device."
            )
          }.font(tokens.typography.caption).padding(tokens.space.page)
        }
      }.padding(tokens.space.page)
    }.background(tokens.colors.surfaceGround.color).navigationTitle("Compositor studio")
      .navigationBarTitleDisplayMode(.inline)
      .onAppear { visible = true }.onDisappear { visible = false }
      .task(id: attempt) {
        let result = await Task.detached(priority: .userInitiated) { PreviewAssets.image() }.value
        guard !Task.isCancelled else { return }
        switch result {
        case .success(let value): image = value
        case .failure(let value): failure = value
        }
      }
  }
  private func control(_ title: String, _ value: Binding<Double>, _ range: ClosedRange<Double>)
    -> some View
  {
    VStack(alignment: .leading) {
      Text("\(title) · \(value.wrappedValue, specifier: "%.2f")").font(tokens.typography.caption)
      Slider(value: value, in: range).accessibilityLabel(title)
    }
  }
  private func gesture(_ value: CanvasGesture) {
    switch value {
    case .drag(let x, let y, let dx, let dy):
      switch tool {
      case .layer:
        layerX = min(1, max(0, layerX + Double(dx)))
        layerY = min(1, max(0, layerY + Double(dy)))
      case .mask:
        maskX = Double(min(1, max(0, x)))
        maskY = Double(min(1, max(0, y)))
      case .compare: comparison = Double(min(1, max(0, x)))
      }
    case .zoom(let factor): scale = min(1, max(0.15, scale * Double(factor)))
    }
  }
  private func reset() {
    opacity = 0.85
    scale = 0.65
    radius = 0.32
    feather = 0.12
    blur = 0
    glow = 0.35
    comparison = 0.5
    layerX = 0.5
    layerY = 0.5
    maskX = 0.5
    maskY = 0.5
    maskEnabled = true
    blend = .normal
    tool = .mask
  }
}
