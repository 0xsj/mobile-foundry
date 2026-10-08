import Foundation
import Metal
import XCTest

@testable import FoundryGraphics

final class MetalCompositorTests: XCTestCase {
  func testActualCompositePixelsPassesAndResourceReuse() throws {
    guard let device = MTLCreateSystemDefaultDevice() else {
      throw XCTSkip("Native Metal required")
    }
    let pipeline = try PreviewPipeline(device: device)
    let queue = try XCTUnwrap(device.makeCommandQueue())
    let base = try XCTUnwrap(RasterImage(width: 1, height: 1, rgba: Data([128, 128, 128, 255])))
    let overlay = try XCTUnwrap(
      AlphaImage(
        width: 2, height: 2,
        rgba: Data([255, 0, 0, 128, 255, 0, 0, 128, 0, 0, 255, 128, 0, 0, 255, 128])))
    func render(_ settings: CompositeSettings, overlay layer: AlphaImage? = nil, width: Int = 128)
      throws -> [UInt8]
    {
      let content = PreviewContent.composite(base, layer ?? overlay, settings)
      try pipeline.prepare(content)
      let d = MTLTextureDescriptor.texture2DDescriptor(
        pixelFormat: .bgra8Unorm, width: width, height: 96, mipmapped: false)
      d.storageMode = .shared
      d.usage = .renderTarget
      let color = try XCTUnwrap(device.makeTexture(descriptor: d))
      d.pixelFormat = .depth32Float
      let depth = try XCTUnwrap(device.makeTexture(descriptor: d))
      let pass = MTLRenderPassDescriptor()
      pass.colorAttachments[0].texture = color
      pass.colorAttachments[0].loadAction = .clear
      pass.colorAttachments[0].storeAction = .store
      pass.depthAttachment.texture = depth
      pass.depthAttachment.loadAction = .clear
      pass.depthAttachment.clearDepth = 1
      let command = try XCTUnwrap(queue.makeCommandBuffer())
      try pipeline.preprocess(command, width: width, height: 96, content: content)
      let encoder = try XCTUnwrap(command.makeRenderCommandEncoder(descriptor: pass))
      pipeline.encode(encoder, width: width, height: 96, time: 0, content: content)
      encoder.endEncoding()
      command.commit()
      command.waitUntilCompleted()
      XCTAssertEqual(command.status, .completed, "\(String(describing: command.error))")
      var pixels = [UInt8](repeating: 0, count: width * 96 * 4)
      color.getBytes(
        &pixels, bytesPerRow: width * 4, from: MTLRegionMake2D(0, 0, width, 96), mipmapLevel: 0)
      return pixels
    }
    func difference(_ a: [UInt8], _ b: [UInt8]) -> Double {
      Double(zip(a, b).reduce(0) { $0 + abs(Int($1.0) - Int($1.1)) }) / Double(a.count)
    }
    let normal = try render(
      .init(opacity: 1, scale: 0.8, glow: 0, comparison: 0, maskEnabled: false))
    let compositor = try XCTUnwrap(pipeline.compositor)
    XCTAssertEqual(compositor.uploads, 2)
    XCTAssertEqual(compositor.targetAllocations, 3)
    XCTAssertEqual(compositor.targetBytes, 128 * 96 * 4 * 3)
    let top = (25 * 128 + 64) * 4
    let bottom = (71 * 128 + 64) * 4
    XCTAssertGreaterThan(normal[top + 2], normal[top])
    XCTAssertGreaterThan(normal[bottom], normal[bottom + 2])
    // At this pixel the overlay is uniform half-alpha red. Linear-light compositing gives ~205/92/92.
    XCTAssertEqual(Double(normal[top + 2]), 205, accuracy: 3)
    XCTAssertEqual(Double(normal[top + 1]), 92, accuracy: 3)
    for blend in [CompositeBlend.multiply, .screen] {
      XCTAssertGreaterThan(
        difference(
          normal,
          try render(
            .init(opacity: 1, scale: 0.8, glow: 0, comparison: 0, maskEnabled: false, blend: blend))
        ), 3)
    }
    let masked = try render(
      .init(
        opacity: 1, scale: 0.8, maskCenter: .init(x: 0.3, y: 0.5), radius: 0.18, feather: 0.04,
        glow: 0, comparison: 0))
    XCTAssertGreaterThan(difference(normal, masked), 5)
    XCTAssertGreaterThan(
      difference(
        masked,
        try render(
          .init(
            opacity: 1, scale: 0.8, maskCenter: .init(x: 0.3, y: 0.5), radius: 0.18, feather: 0.04,
            blur: 24, glow: 1, comparison: 0))), 1)
    let original = try render(.init(comparison: 1))
    XCTAssertLessThan(difference(original, try render(.init(opacity: 0, comparison: 0))), 0.01)
    XCTAssertEqual(compositor.uploads, 2)
    XCTAssertEqual(compositor.targetAllocations, 3)
    let invisible = try XCTUnwrap(AlphaImage(width: 1, height: 1, rgba: Data([0, 255, 0, 0])))
    XCTAssertLessThan(
      difference(original, try render(.init(blur: 24, glow: 1, comparison: 0), overlay: invisible)),
      0.01)
    XCTAssertEqual(compositor.uploads, 3)
    _ = try render(.init(), width: 96)
    XCTAssertEqual(compositor.targetAllocations, 6)
    XCTAssertEqual(compositor.targetBytes, 96 * 96 * 4 * 3)
    try pipeline.prepare(.image(base, .init(), .init(), comparison: 1))
    XCTAssertNil(pipeline.compositor)
  }
}
