/// Only a monotonic active-frame delta changes phase. Resume never includes idle time.
public struct EffectClock: Sendable {
    public private(set) var elapsed: Double = 0
    private var previous: Double?
    public init() {}
    public mutating func suspend() { previous = nil }
    public mutating func frame(at now: Double, running: Bool) -> Double {
        guard running, now.isFinite else { suspend(); return elapsed }
        if let previous { elapsed += min(0.1, max(0, now - previous)) }
        previous = now
        return elapsed
    }
}
