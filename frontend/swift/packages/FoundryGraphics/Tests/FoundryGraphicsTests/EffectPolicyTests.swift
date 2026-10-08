import Foundation
import Testing
@testable import FoundryGraphics

private struct Cases: Decodable {
    struct Point: Decodable { let x, y, expectedX, expectedY: Float }
    struct Size: Decodable { let quality: String; let width, height: Double; let expectedWidth, expectedHeight: Int? }
    struct Frame: Decodable { let now, elapsed: Double; let running: Bool }
    struct Progress: Decodable { let input, expected: Float }
    struct Sample: Decodable { let weight, radius, expectedWeight, expectedRadius: Float }
    let kinds: [String: Float]; let progress: [Progress]; let samples: [Sample]
    let points: [Point]; let sizes: [Size]; let clock: [Frame]
}

@Test func suppliedContentIsBoundedAndOwned() throws {
    let fixture = try cases()
    for (name, index) in fixture.kinds { #expect(EffectKind(rawValue: name)?.shaderIndex == index) }
    for row in fixture.progress { #expect(EffectSettings(progress: row.input).progress == row.expected) }
    #expect(EffectSettings(progress: .nan).progress == 0.5)
    for row in fixture.samples {
        let sample = EffectFieldSample(point: .init(), weight: row.weight, radius: row.radius)
        #expect(sample.weight == row.expectedWeight && sample.radius == row.expectedRadius)
    }
    let sample = EffectFieldSample(point: .init(), weight: .infinity, radius: .nan)
    #expect(sample.weight == 0 && sample.radius == 0.18)
    var source = [EffectFieldSample](repeating: .init(point: .init()), count: 20)
    let settings = EffectSettings(samples: source)
    source.removeAll()
    #expect(settings.samples.count == 12)
    #expect(settings.samples.first?.weight == 1)
    #expect(EffectSettings().samples.isEmpty)
}
private func cases() throws -> Cases {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    return try JSONDecoder().decode(Cases.self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/graphics/effects.json")))
}

@Test func sharedInputAndResolutionBudgetsAreBounded() throws {
    let fixture = try cases()
    for row in fixture.points {
        let point = EffectPoint(x: row.x, y: row.y)
        #expect(point.x == row.expectedX && point.y == row.expectedY)
    }
    #expect(EffectPoint(x: .nan, y: .infinity) == .init())
    #expect(EffectSettings(strength: .nan).strength == 0.65)
    #expect(EffectSettings(strength: -1).strength == 0)
    #expect(EffectSettings(strength: 5).strength == 1)
    for row in fixture.sizes {
        let size = try #require(EffectQuality(rawValue: row.quality)).resolution(width: row.width, height: row.height)
        #expect(size?.width == row.expectedWidth && size?.height == row.expectedHeight)
    }
    #expect(EffectQuality.balanced.resolution(width: .infinity, height: 100) == nil)
    #expect(EffectQuality.economy.framesPerSecond == 30 && EffectQuality.economy.steps == 32)
    #expect(EffectQuality.balanced.framesPerSecond == 60 && EffectQuality.balanced.steps == 64)
}

@Test func sharedClockExcludesPausesAndBoundsStalls() throws {
    var clock = EffectClock()
    for row in try cases().clock { #expect(abs(clock.frame(at: row.now, running: row.running) - row.elapsed) < 0.000001) }
    let phase = clock.elapsed
    clock.suspend()
    #expect(clock.frame(at: 1000, running: true) == phase)
    #expect(clock.frame(at: .nan, running: true) == phase)
    #expect(clock.frame(at: 1001, running: true) == phase)
}
