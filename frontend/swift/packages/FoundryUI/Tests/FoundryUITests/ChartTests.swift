@testable import FoundryUI
import Testing

@Test func sparklineNormalizationKeepsFiniteExtremeAndSignedSamplesInsideBounds() {
    let points = normalizedSparkline([-.greatestFiniteMagnitude, 0, .greatestFiniteMagnitude])
    #expect(points.map(\.x) == [0, 0.5, 1])
    #expect(points.map(\.y) == [1, 0.5, 0])
    #expect(points.allSatisfy { $0.x.isFinite && $0.y.isFinite && (0...1).contains($0.y) })
    #expect(normalizedSparkline([-30, -20, -10]).map(\.y) == [1, 0.5, 0])
}
@Test func sparklineNormalizationHandlesEmptySingleAndConstantSamples() {
    #expect(normalizedSparkline([]).isEmpty)
    let single = normalizedSparkline([42])
    #expect(single.count == 1 && single[0].x == 0.5 && single[0].y == 0.5)
    #expect(normalizedSparkline([0, 0, 0]).map(\.y) == [0.5, 0.5, 0.5])
    #expect(normalizedSparkline([.greatestFiniteMagnitude, .greatestFiniteMagnitude]).map(\.y) == [0.5, 0.5])
}
