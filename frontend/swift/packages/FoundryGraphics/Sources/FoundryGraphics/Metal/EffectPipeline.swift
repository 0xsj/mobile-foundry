import Foundation
import Metal

/// Owns compiled GPU state. No UI, scheduler or application state lives here.
final class EffectPipeline {
    let state: any MTLRenderPipelineState
    init(device: any MTLDevice, pixelFormat: MTLPixelFormat = .bgra8Unorm) throws {
        guard let url = Bundle.module.url(forResource: "Effects", withExtension: "metal", subdirectory: "Shaders") else {
            throw PipelineError.missingShader
        }
        let library = try device.makeLibrary(source: String(contentsOf: url, encoding: .utf8), options: nil)
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "effectVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "effectFragment")
        descriptor.colorAttachments[0].pixelFormat = pixelFormat
        state = try device.makeRenderPipelineState(descriptor: descriptor)
    }

    func encode(_ encoder: any MTLRenderCommandEncoder, width: Int, height: Int, time: Double, settings: EffectSettings) {
        // Matches two float4 values in Effects.metal (32 bytes, alignment 16).
        var uniforms = Uniforms(viewport: .init(Float(width), Float(height), Float(time), settings.strength),
                                interaction: .init(settings.point.x, settings.point.y, settings.effect == .ripple ? 0 : 1, Float(settings.quality.steps)))
        encoder.setRenderPipelineState(state)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    }
    private struct Uniforms { var viewport: SIMD4<Float>; var interaction: SIMD4<Float> }
    private enum PipelineError: Error { case missingShader }
}
