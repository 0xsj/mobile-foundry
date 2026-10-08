import Metal
import XCTest
@testable import FoundryGraphics

final class MetalEffectsTests: XCTestCase {
    func testRealShaderPixelsVaryWithEffectInteractionAndTime() throws {
        guard let device = MTLCreateSystemDefaultDevice() else { throw XCTSkip("A native Metal device is required for shader execution.") }
        let pipeline = try EffectPipeline(device: device)
        let queue = try XCTUnwrap(device.makeCommandQueue())
        func render(_ effect: EffectKind, point: EffectPoint = .init(), time: Double = 0, quality: EffectQuality = .balanced) throws -> [UInt8] {
            let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: 128, height: 96, mipmapped: false)
            descriptor.storageMode = .shared; descriptor.usage = [.renderTarget]
            let texture = try XCTUnwrap(device.makeTexture(descriptor: descriptor))
            let pass = MTLRenderPassDescriptor()
            pass.colorAttachments[0].texture = texture; pass.colorAttachments[0].loadAction = .clear; pass.colorAttachments[0].storeAction = .store
            let command = try XCTUnwrap(queue.makeCommandBuffer())
            let encoder = try XCTUnwrap(command.makeRenderCommandEncoder(descriptor: pass))
            pipeline.encode(encoder, width: 128, height: 96, time: time, settings: .init(effect: effect, quality: quality, point: point))
            encoder.endEncoding(); command.commit(); command.waitUntilCompleted()
            XCTAssertEqual(command.status, .completed, "\(String(describing: command.error))")
            var bytes = [UInt8](repeating: 0, count: 128 * 96 * 4)
            texture.getBytes(&bytes, bytesPerRow: 128 * 4, from: MTLRegionMake2D(0, 0, 128, 96), mipmapLevel: 0)
            return bytes
        }
        func difference(_ a: [UInt8], _ b: [UInt8]) -> Double {
            Double(zip(a, b).reduce(0) { $0 + abs(Int($1.0) - Int($1.1)) }) / Double(a.count)
        }
        let ripple = try render(.ripple)
        let orbit = try render(.orbit)
        XCTAssertTrue(stride(from: 3, to: ripple.count, by: 4).allSatisfy { ripple[$0] == 255 })
        XCTAssertGreaterThan(Int(ripple.max()!) - Int(ripple.min()!), 50)
        XCTAssertGreaterThan(difference(ripple, orbit), 5)
        XCTAssertGreaterThan(difference(ripple, try render(.ripple, point: .init(x: 0.2, y: 0.8))), 2)
        XCTAssertGreaterThan(difference(orbit, try render(.orbit, point: .init(x: 0.2, y: 0.8))), 1)
        XCTAssertGreaterThan(difference(ripple, try render(.ripple, time: 2)), 2)
        XCTAssertGreaterThan(difference(orbit, try render(.orbit, time: 2)), 1)
        XCTAssertGreaterThan(try render(.orbit, quality: .economy).filter { $0 > 64 }.count, 100)
    }
}
