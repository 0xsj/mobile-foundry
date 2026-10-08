import Foundation
import Testing
@testable import FoundryGraphics

private struct Cases: Decodable {
    struct Point: Decodable { let x, y, expectedX, expectedY: Float }
    struct Size: Decodable { let quality: String; let width, height: Double; let expectedWidth, expectedHeight: Int? }
    struct Frame: Decodable { let now, elapsed: Double; let running: Bool }
    let points: [Point]; let sizes: [Size]; let clock: [Frame]
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
