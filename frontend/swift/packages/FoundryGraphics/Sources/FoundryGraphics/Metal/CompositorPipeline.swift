import Foundation
import Metal

/// Four passes, retained source uploads and three linear-premultiplied targets.
final class CompositorPipeline {
  private let device: any MTLDevice
  private let offscreen, final: any MTLRenderPipelineState
  private var base: RasterImage?
  private var overlay: AlphaImage?
  private var baseTexture, overlayTexture: (any MTLTexture)?
  private var targets: [any MTLTexture] = []
  private(set) var uploads = 0
  private(set) var targetAllocations = 0
  var inputBytes: Int {
    (base.map { $0.width * $0.height } ?? 0) * 4 + (overlay.map { $0.width * $0.height } ?? 0) * 4
  }
  var targetBytes: Int { targets.reduce(0) { $0 + $1.width * $1.height * 4 } }

  init(device: any MTLDevice) throws {
    self.device = device
    guard
      let url = Bundle.module.url(
        forResource: "Compositor", withExtension: "metal", subdirectory: "Shaders")
    else { throw PreviewRenderError.shaderMissing }
    let library = try device.makeLibrary(
      source: String(contentsOf: url, encoding: .utf8), options: nil)
    func state(format: MTLPixelFormat, depth: MTLPixelFormat) throws -> any MTLRenderPipelineState {
      let d = MTLRenderPipelineDescriptor()
      d.vertexFunction = library.makeFunction(name: "compositeVertex")
      d.fragmentFunction = library.makeFunction(name: "compositeFragment")
      d.colorAttachments[0].pixelFormat = format
      d.depthAttachmentPixelFormat = depth
      return try device.makeRenderPipelineState(descriptor: d)
    }
    offscreen = try state(format: .rgba8Unorm, depth: .invalid)
    final = try state(format: .bgra8Unorm, depth: .depth32Float)
  }
  func prepare(base: RasterImage, overlay: AlphaImage) throws {
    if self.base !== base {
      baseTexture = try upload(width: base.width, height: base.height, bytes: base.rgba)
      self.base = base
      uploads += 1
    }
    if self.overlay !== overlay {
      overlayTexture = try upload(width: overlay.width, height: overlay.height, bytes: overlay.rgba)
      self.overlay = overlay
      uploads += 1
    }
  }
  private func upload(width: Int, height: Int, bytes: Data) throws -> any MTLTexture {
    let d = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: false)
    d.storageMode = .shared
    d.usage = .shaderRead
    guard let texture = device.makeTexture(descriptor: d) else {
      throw PreviewRenderError.resourceUnavailable
    }
    linearPremultiplied(bytes).withUnsafeBytes {
      texture.replace(
        region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0, withBytes: $0.baseAddress!,
        bytesPerRow: width * 4)
    }
    return texture
  }
  func preprocess(
    _ command: any MTLCommandBuffer, width: Int, height: Int, settings: CompositeSettings
  ) throws {
    if targets.first?.width != width || targets.first?.height != height {
      let d = MTLTextureDescriptor.texture2DDescriptor(
        pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: false)
      d.storageMode = .private
      d.usage = [.shaderRead, .renderTarget]
      targets = try (0..<3).map { _ in
        guard let texture = device.makeTexture(descriptor: d) else {
          throw PreviewRenderError.resourceUnavailable
        }
        return texture
      }
      targetAllocations += 3
    }
    guard let overlayTexture else { throw PreviewRenderError.resourceUnavailable }
    for index in 0..<3 {
      let pass = MTLRenderPassDescriptor()
      pass.colorAttachments[0].texture = targets[index]
      pass.colorAttachments[0].loadAction = .dontCare
      pass.colorAttachments[0].storeAction = .store
      guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else {
        throw PreviewRenderError.resourceUnavailable
      }
      encoder.label = ["Layer and mask", "Horizontal blur", "Vertical blur"][index]
      let source = index == 0 ? overlayTexture : targets[index - 1]
      encode(
        encoder, width: width, height: height, pass: index, settings: settings,
        textures: [source, source, source], pipeline: offscreen)
      encoder.endEncoding()
    }
  }
  func composite(
    _ encoder: any MTLRenderCommandEncoder, width: Int, height: Int, settings: CompositeSettings
  ) {
    guard let baseTexture, targets.count == 3 else { return }
    encode(
      encoder, width: width, height: height, pass: 3, settings: settings,
      textures: [baseTexture, targets[0], targets[2]], pipeline: final)
  }
  private func encode(
    _ encoder: any MTLRenderCommandEncoder, width: Int, height: Int, pass: Int,
    settings s: CompositeSettings, textures: [any MTLTexture], pipeline: any MTLRenderPipelineState
  ) {
    let spread = max(s.blur, s.glow > 0 ? 8 : 0)
    var u = Uniforms(
      viewport: .init(Float(width), Float(height), Float(pass), pass == 3 ? s.blur : spread),
      layer: .init(s.layerCenter.x, s.layerCenter.y, s.scale, s.opacity),
      mask: .init(s.maskCenter.x, s.maskCenter.y, s.radius, s.feather),
      style: .init(s.maskEnabled ? 1 : 0, s.blend.shaderIndex, s.glow, s.comparison),
      imageInfo: .init(
        Float(base!.width), Float(base!.height), Float(overlay!.width), Float(overlay!.height)))
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 0)
    for i in textures.indices { encoder.setFragmentTexture(textures[i], index: i) }
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
  }
  private struct Uniforms { var viewport, layer, mask, style, imageInfo: SIMD4<Float> }
}
