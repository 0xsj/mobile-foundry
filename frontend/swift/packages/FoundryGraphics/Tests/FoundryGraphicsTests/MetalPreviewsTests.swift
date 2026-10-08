import Foundation
import Metal
import XCTest

@testable import FoundryGraphics

final class MetalPreviewsTests: XCTestCase {
  func testActualImageFilteringOrientationAndMeshMaterials() throws {
    guard let device = MTLCreateSystemDefaultDevice() else {
      throw XCTSkip("Native Metal device required")
    }
    let pipeline = try PreviewPipeline(device: device)
    let queue = try XCTUnwrap(device.makeCommandQueue())
    func render(_ content: PreviewContent, time: Double = 0) -> [UInt8] {
      do {
        try pipeline.prepare(content)
        let d = MTLTextureDescriptor.texture2DDescriptor(
          pixelFormat: .bgra8Unorm, width: 128, height: 96, mipmapped: false)
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
        let encoder = try XCTUnwrap(command.makeRenderCommandEncoder(descriptor: pass))
        pipeline.encode(encoder, width: 128, height: 96, time: time, content: content)
        encoder.endEncoding()
        command.commit()
        command.waitUntilCompleted()
        XCTAssertEqual(command.status, .completed, "\(String(describing:command.error))")
        var bytes = [UInt8](repeating: 0, count: 128 * 96 * 4)
        color.getBytes(
          &bytes, bytesPerRow: 128 * 4, from: MTLRegionMake2D(0, 0, 128, 96), mipmapLevel: 0)
        return bytes
      } catch {
        XCTFail("\(error)")
        return []
      }
    }
    func difference(_ a: [UInt8], _ b: [UInt8]) -> Double {
      Double(zip(a, b).reduce(0) { $0 + abs(Int($1.0) - Int($1.1)) }) / Double(a.count)
    }
    let pixels: [UInt8] = (0..<12).flatMap { i -> [UInt8] in
      i < 4 ? [160, 40, 20, 255] : [20, 80, 160, 255]
    }
    let image = try XCTUnwrap(RasterImage(width: 4, height: 3, rgba: Data(pixels)))
    let original = render(.image(image, .init(), .init(), comparison: 1))
    XCTAssertEqual(original.count, 128 * 96 * 4)
    guard original.count == 128 * 96 * 4 else { return }
    // BGRA readback: red top row, blue bottom row; no accidental vertical flip.
    XCTAssertGreaterThan(original[(12 * 128 + 16) * 4 + 2], original[(12 * 128 + 16) * 4])
    XCTAssertGreaterThan(original[(84 * 128 + 16) * 4], original[(84 * 128 + 16) * 4 + 2])
    XCTAssertGreaterThan(
      difference(original, render(.image(image, .init(exposure: 1), .init(), comparison: 0))), 15)
    XCTAssertGreaterThan(
      difference(original, render(.image(image, .init(saturation: 0), .init(), comparison: 0))), 10)
    XCTAssertGreaterThan(
      difference(original, render(.image(image, .init(vignette: 1), .init(), comparison: 0))), 2)
    XCTAssertGreaterThan(
      difference(original, render(.image(image, .init(), .init(zoom: 2, y: 0.2), comparison: 1))), 5
    )
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    struct Asset: Decodable { let vertices: [Float] }
    let asset = try JSONDecoder().decode(
      Asset.self,
      from: Data(contentsOf: root.appendingPathComponent("assets/source/studio-lamp.json")))
    let mesh = try XCTUnwrap(PreviewMesh(vertices: asset.vertices))
    let porcelain = render(.product(mesh, .init(), .porcelain))
    XCTAssertLessThan(
      difference(porcelain, render(.product(mesh, .init(), .porcelain), time: .pi * 8)), 0.01)
    XCTAssertGreaterThan(difference(porcelain, render(.product(mesh, .init(), .cobalt))), 2)
    XCTAssertGreaterThan(difference(porcelain, render(.product(mesh, .init(), .bronze))), 2)
    XCTAssertGreaterThan(
      difference(
        porcelain, render(.product(mesh, .init(yaw: 1.2, pitch: 0.4, distance: 3.2), .porcelain))),
      3)
  }
}
