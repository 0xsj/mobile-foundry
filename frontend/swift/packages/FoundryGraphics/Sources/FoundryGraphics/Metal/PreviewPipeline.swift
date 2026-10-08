import Foundation
import Metal

enum PreviewRenderError: Error { case resourceUnavailable, shaderMissing }

/// Retains GPU assets by admitted CPU object identity, independently of edit values.
final class PreviewPipeline {
  private let device: any MTLDevice
  private let full: any MTLRenderPipelineState
  private let mesh: any MTLRenderPipelineState
  private let depth: any MTLDepthStencilState
  private let noDepth: any MTLDepthStencilState
  private let placeholder: any MTLTexture
  private var image: RasterImage?
  private var texture: (any MTLTexture)?
  private var geometry: PreviewMesh?
  private var buffer: (any MTLBuffer)?

  init(device: any MTLDevice) throws {
    self.device = device
    guard
      let url = Bundle.module.url(
        forResource: "Previews", withExtension: "metal", subdirectory: "Shaders")
    else { throw PreviewRenderError.shaderMissing }
    let library = try device.makeLibrary(
      source: String(contentsOf: url, encoding: .utf8), options: nil)
    func pipeline(vertex: String) throws -> any MTLRenderPipelineState {
      let descriptor = MTLRenderPipelineDescriptor()
      descriptor.vertexFunction = library.makeFunction(name: vertex)
      descriptor.fragmentFunction = library.makeFunction(name: "previewFragment")
      descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
      descriptor.depthAttachmentPixelFormat = .depth32Float
      return try device.makeRenderPipelineState(descriptor: descriptor)
    }
    full = try pipeline(vertex: "previewFull")
    mesh = try pipeline(vertex: "previewMesh")
    let descriptor = MTLDepthStencilDescriptor()
    descriptor.depthCompareFunction = .less
    descriptor.isDepthWriteEnabled = true
    guard let depth = device.makeDepthStencilState(descriptor: descriptor) else {
      throw PreviewRenderError.resourceUnavailable
    }
    self.depth = depth
    descriptor.depthCompareFunction = .always
    descriptor.isDepthWriteEnabled = false
    guard let noDepth = device.makeDepthStencilState(descriptor: descriptor),
      let placeholder = device.makeTexture(
        descriptor: .texture2DDescriptor(
          pixelFormat: .rgba8Unorm, width: 1, height: 1, mipmapped: false))
    else { throw PreviewRenderError.resourceUnavailable }
    self.noDepth = noDepth
    self.placeholder = placeholder
  }

  /// Resource creation occurs only for a new asset; normal control redraws reuse it.
  func prepare(_ content: PreviewContent) throws {
    switch content {
    case .image(let next, _, _, _):
      guard image !== next else { return }
      let descriptor = MTLTextureDescriptor.texture2DDescriptor(
        pixelFormat: .rgba8Unorm, width: next.width, height: next.height, mipmapped: false)
      descriptor.storageMode = .shared
      guard let nextTexture = device.makeTexture(descriptor: descriptor) else {
        throw PreviewRenderError.resourceUnavailable
      }
      next.rgba.withUnsafeBytes { bytes in
        nextTexture.replace(
          region: MTLRegionMake2D(0, 0, next.width, next.height), mipmapLevel: 0,
          withBytes: bytes.baseAddress!, bytesPerRow: next.width * 4)
      }
      image = next
      texture = nextTexture
      geometry = nil
      buffer = nil
    case .product(let next, _, _):
      guard geometry !== next else { return }
      let nextBuffer = next.vertices.withUnsafeBytes { bytes in
        device.makeBuffer(
          bytes: bytes.baseAddress!, length: bytes.count, options: .storageModeShared)
      }
      guard let nextBuffer else { throw PreviewRenderError.resourceUnavailable }
      geometry = next
      buffer = nextBuffer
      image = nil
      texture = nil
    }
  }

  func encode(
    _ encoder: any MTLRenderCommandEncoder, width: Int, height: Int, time: Double,
    content: PreviewContent
  ) {
    var u = Uniforms(
      viewport: .init(
        Float(width), Float(height), Float(time.truncatingRemainder(dividingBy: .pi * 8)), 0),
      edit: .zero, image: .zero,
      panCamera: .zero, style: .zero)
    encoder.setFragmentTexture(texture ?? placeholder, index: 0)
    encoder.setDepthStencilState(noDepth)
    switch content {
    case .image(let image, let edit, let view, let comparison):
      u.edit = .init(
        edit.exposure, edit.saturation, edit.vignette,
        comparison.isFinite ? min(1, max(0, comparison)) : 0.5)
      u.image = .init(Float(image.width), Float(image.height), view.zoom, 0)
      u.panCamera = .init(view.x, view.y, 0, 0)
      encoder.setRenderPipelineState(full)
      encoder.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 1)
      encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    case .product(let geometry, let camera, let finish):
      u.viewport.w = 1
      u.panCamera = .init(0, 0, camera.yaw, camera.pitch)
      u.style = .init(camera.distance, Float(ProductFinish.allCases.firstIndex(of: finish)!), 0, 0)
      encoder.setRenderPipelineState(full)
      encoder.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 1)
      encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
      u.viewport.w = 2
      encoder.setRenderPipelineState(mesh)
      encoder.setDepthStencilState(depth)
      encoder.setVertexBuffer(buffer, offset: 0, index: 0)
      encoder.setVertexBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 1)
      encoder.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 1)
      encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: geometry.vertexCount)
    }
  }
  private struct Uniforms { var viewport, edit, image, panCamera, style: SIMD4<Float> }
}
