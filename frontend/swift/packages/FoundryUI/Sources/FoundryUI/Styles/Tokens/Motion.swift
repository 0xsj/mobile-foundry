import SwiftUI

public struct FoundryMotion: Equatable, Sendable {
    public let reduced: Bool
    public var milliseconds: [Int] { reduced ? [0, 0, 0] : [120, 200, 280] }
    public var standardAnimation: Animation {
        .timingCurve(0.2, 0, 0, 1, duration: Double(milliseconds[1]) / 1000)
    }
}
