import Foundation

public struct PhysicsVector: Equatable, Sendable {
    public let x: Double
    public let y: Double
    public init(x: Double = 0, y: Double = 0) { self.x = x; self.y = y }
    public var length: Double { hypot(x, y) }
    public var isFinite: Bool { x.isFinite && y.isFinite }
    public static func + (a: Self, b: Self) -> Self { .init(x: a.x + b.x, y: a.y + b.y) }
    public static func - (a: Self, b: Self) -> Self { .init(x: a.x - b.x, y: a.y - b.y) }
    public static func * (a: Self, b: Double) -> Self { .init(x: a.x * b, y: a.y * b) }
    public func dot(_ other: Self) -> Double { x * other.x + y * other.y }
}

public enum ThrowTarget: String, CaseIterable, Sendable { case hoop, landingZone }
public enum ThrowPhase: String, Sendable { case ready, flying, settled }

public struct PhysicsSettings: Equatable, Sendable {
    public let gravity: Double
    public let restitution: Double
    public init(gravity: Double = 9.81, restitution: Double = 0.65) {
        self.gravity = gravity.isFinite ? min(20, max(4, gravity)) : 9.81
        self.restitution = restitution.isFinite ? min(0.9, max(0, restitution)) : 0.65
    }
}

/// A bounded one-ball world in metres, with +Y up. The caller owns scheduling and rendering.
/// Fixed steps intentionally discard wall-clock stalls beyond 100 ms; pausing never calls advance.
public struct ThrowSimulation: Equatable, Sendable {
    public static let width = 10.0
    public static let height = 7.0
    public static let ballRadius = 0.22
    public static let rimRadius = 0.09
    public static let hoopCenter = PhysicsVector(x: 7.75, y: 3.3)
    public static let hoopHalfWidth = 0.65
    public static let zoneMin = 6.5
    public static let zoneMax = 8.5
    public static let launchPosition = PhysicsVector(x: 1.15, y: 0.65)
    public static let timeStep = 1.0 / 120.0

    public private(set) var target: ThrowTarget
    public private(set) var settings: PhysicsSettings
    public private(set) var position = launchPosition
    public private(set) var velocity = PhysicsVector()
    public private(set) var phase = ThrowPhase.ready
    public private(set) var shots = 0
    public private(set) var scores = 0
    public private(set) var scored = false
    private var accumulator = 0.0
    private var flightTime = 0.0
    private var restTime = 0.0
    private var firstLanding = false

    public init(target: ThrowTarget = .hoop, settings: PhysicsSettings = .init()) {
        self.target = target; self.settings = settings
    }

    /// Reset the ball while keeping this session's attempts and scores.
    public mutating func resetBall() {
        position = Self.launchPosition; velocity = .init(); phase = .ready
        scored = false; accumulator = 0; flightTime = 0; restTime = 0; firstLanding = false
    }
    public mutating func configure(target: ThrowTarget, settings: PhysicsSettings) {
        if self.target != target { shots = 0; scores = 0 }
        self.target = target; self.settings = settings; resetBall()
    }

    /// Drag from the ball towards the desired direction. Large gestures are capped at 14 m/s.
    public static func aimVelocity(displacement: PhysicsVector) -> PhysicsVector {
        guard displacement.isFinite, displacement.length.isFinite else { return .init() }
        return displacement * min(2.8, 14 / max(1e-12, displacement.length))
    }
    @discardableResult public mutating func launch(velocity: PhysicsVector) -> Bool {
        guard phase == .ready, velocity.isFinite, velocity.length.isFinite, velocity.length >= 0.2 else { return false }
        self.velocity = velocity.length > 14 ? velocity * (14 / velocity.length) : velocity
        phase = .flying; shots += 1; return true
    }

    /// Advance using 120 Hz semi-implicit Euler integration, circle contacts and normal impulses.
    public mutating func advance(elapsed: Double) {
        guard phase == .flying, elapsed.isFinite, elapsed > 0 else { return }
        accumulator += min(0.1, elapsed)
        var steps = 0
        while accumulator + 1e-12 >= Self.timeStep && steps < 12 && phase == .flying {
            step(); accumulator = max(0, accumulator - Self.timeStep); steps += 1
        }
    }
    /// Preview runs a copy through exactly the same solver as the actual shot.
    public func trajectory(velocity: PhysicsVector) -> [PhysicsVector] {
        var preview = self
        guard preview.launch(velocity: velocity) else { return [] }
        var points = [preview.position]
        for _ in 0..<48 {
            preview.advance(elapsed: Self.timeStep * 6)
            points.append(preview.position)
            if preview.position.y <= Self.ballRadius + 0.01 || preview.scored { break }
        }
        return points
    }

    private mutating func step() {
        let dt = Self.timeStep, r = Self.ballRadius
        let previous = position
        velocity = velocity + PhysicsVector(y: -settings.gravity * dt)
        position = position + velocity * dt
        flightTime += dt

        if position.x < r { position = .init(x: r, y: position.y); bounce(normal: .init(x: 1)) }
        if position.x > Self.width - r { position = .init(x: Self.width - r, y: position.y); bounce(normal: .init(x: -1)) }
        if position.y > Self.height - r { position = .init(x: position.x, y: Self.height - r); bounce(normal: .init(y: -1)) }

        var hitRim = false
        if target == .hoop {
            for x in [Self.hoopCenter.x - Self.hoopHalfWidth, Self.hoopCenter.x + Self.hoopHalfWidth] {
                hitRim = collide(center: .init(x: x, y: Self.hoopCenter.y), radius: Self.rimRadius) || hitRim
            }
            // Backboard: a vertical capsule, with the same circle contact solver at its closest point.
            let boardX = Self.hoopCenter.x + Self.hoopHalfWidth + 0.35
            _ = collide(center: .init(x: boardX, y: min(Self.hoopCenter.y + 1.3, max(Self.hoopCenter.y - 0.15, position.y))), radius: 0.07)
            if !scored && !hitRim && previous.y > Self.hoopCenter.y && position.y <= Self.hoopCenter.y && velocity.y < 0 {
                let fraction = (previous.y - Self.hoopCenter.y) / (previous.y - position.y)
                let crossingX = previous.x + (position.x - previous.x) * fraction
                let clearance = Self.hoopHalfWidth - Self.rimRadius - r
                if abs(crossingX - Self.hoopCenter.x) < clearance { score() }
            }
        }
        if position.y < r {
            if target == .landingZone && !firstLanding && !scored && position.x - r >= Self.zoneMin && position.x + r <= Self.zoneMax { score() }
            firstLanding = true
            position = .init(x: position.x, y: r)
            bounce(normal: .init(y: 1))
            // Ground friction; small impacts sleep instead of bouncing forever.
            velocity = .init(x: velocity.x * 0.985, y: abs(velocity.y) < 0.35 ? 0 : velocity.y)
        }
        if position.y <= r + 0.001 && velocity.length < 0.25 { restTime += dt } else { restTime = 0 }
        if restTime >= 0.3 || flightTime >= 12 { phase = .settled; velocity = .init(); accumulator = 0 }
    }
    private mutating func bounce(normal: PhysicsVector) {
        let incoming = velocity.dot(normal)
        if incoming < 0 { velocity = velocity - normal * ((1 + settings.restitution) * incoming) }
    }
    private mutating func collide(center: PhysicsVector, radius: Double) -> Bool {
        let offset = position - center, distance = offset.length
        let separation = Self.ballRadius + radius
        guard distance < separation else { return false }
        let normal = distance > 1e-9 ? offset * (1 / distance) : PhysicsVector(y: 1)
        position = center + normal * separation
        bounce(normal: normal)
        return true
    }
    private mutating func score() { scored = true; scores += 1 }
}
