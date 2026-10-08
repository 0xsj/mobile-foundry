import Foundation
import Metal

/// Owns compiled GPU state. No UI, scheduler or application state lives here.
final class EffectPipeline {
    let state: any MTLRenderPipelineState
    private var sampleUniforms = [SIMD4<Float>](repeating: .zero, count: EffectSettings.maximumFieldSamples)
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
        // Three aligned float4s in buffer 0; twelve sample float4s in buffer 1.
        var uniforms = Uniforms(viewport: .init(Float(width), Float(height), Float(time), settings.strength),
                                interaction: .init(settings.point.x, settings.point.y, settings.effect.shaderIndex, Float(settings.quality.steps)),
                                content: .init(settings.progress, Float(settings.samples.count), 0, 0))
        for i in sampleUniforms.indices {
            if i < settings.samples.count {
                let sample = settings.samples[i]
                sampleUniforms[i] = .init(sample.point.x, sample.point.y, sample.weight, sample.radius)
            } else { sampleUniforms[i] = .zero }
        }
        encoder.setRenderPipelineState(state)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
        sampleUniforms.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 1) }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    }
    private struct Uniforms { var viewport: SIMD4<Float>; var interaction: SIMD4<Float>; var content: SIMD4<Float> }
    private enum PipelineError: Error { case missingShader }
}
