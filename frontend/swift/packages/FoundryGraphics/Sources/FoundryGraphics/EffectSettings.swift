import Foundation
import FoundryKernel

public enum EffectKind: String, CaseIterable, Sendable {
    case ripple, orbit, flow, material, liquid, particles, field
    /// Explicit native shader protocol, independent of declaration order.
    public var shaderIndex: Float {
        switch self {
        case .ripple: 0; case .orbit: 1; case .flow: 2; case .material: 3
        case .liquid: 4; case .particles: 5; case .field: 6
        }
    }
}
public enum EffectQuality: String, CaseIterable, Sendable {
    case economy, balanced
    public var framesPerSecond: Int { self == .economy ? 30 : 60 }
    public var steps: Int { self == .economy ? 32 : 64 }
    public func resolution(width: Double, height: Double) -> (width: Int, height: Int)? {
        guard width.isFinite, height.isFinite, width > 0, height > 0 else { return nil }
        let scale = min(self == .economy ? 0.5 : 0.75, (self == .economy ? 900.0 : 1600.0) / max(width, height))
        return (max(1, Int(floor(width * scale))), max(1, Int(floor(height * scale))))
    }
}

public struct EffectPoint: Equatable, Sendable {
    public let x: Float
    public let y: Float
    public init(x: Float = 0.5, y: Float = 0.5) {
        self.x = x.isFinite ? min(1, max(0, x)) : 0.5
        self.y = y.isFinite ? min(1, max(0, y)) : 0.5
    }
}

/// A supplied scalar density contribution, not a sensor measurement or simulation.
public struct EffectFieldSample: Equatable, Sendable {
    public let point: EffectPoint
    public let weight: Float
    public let radius: Float
    public init(point: EffectPoint, weight: Float = 1, radius: Float = 0.18) {
        self.point = point
        self.weight = weight.isFinite ? min(1, max(0, weight)) : 0
        self.radius = radius.isFinite ? min(0.5, max(0.03, radius)) : 0.18
    }
}

public struct EffectSettings: Equatable, Sendable {
    public static let maximumFieldSamples = 12
    public let effect: EffectKind
    public let quality: EffectQuality
    public let strength: Float
    public let point: EffectPoint
    /// Liquid's true completion or Particles' finite playhead. Never derived from renderer time.
    public let progress: Float
    public let samples: [EffectFieldSample]
    public init(effect: EffectKind = .ripple, quality: EffectQuality = .balanced,
                strength: Float = 0.65, point: EffectPoint = .init(),
                progress: Float = 0.5, samples: [EffectFieldSample] = []) {
        self.effect = effect; self.quality = quality; self.point = point
        self.strength = strength.isFinite ? min(1, max(0, strength)) : 0.65
        self.progress = progress.isFinite ? min(1, max(0, progress)) : 0.5
        self.samples = Array(samples.prefix(Self.maximumFieldSamples))
    }
}

public struct EffectStatistics: Sendable {
    public let submittedFrames: Int
    public let submissionsPerSecond: Double
    public let width: Int
    public let height: Int
}

public enum EffectEvent: Sendable {
    case ready(String)
    case statistics(EffectStatistics)
    case failed(Failure)
}
