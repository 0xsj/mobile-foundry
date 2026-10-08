import Foundation
import Testing
import FoundryUI

private struct TokenFixture: Decodable {
    struct Color: Decodable { let rgb: String; let alpha: Double }
    let presets: [String: [String: [String: Color]]]
    let space: [Int]
    let radii: [Int]
    let motionMilliseconds: [Int]
    let minimumInteractive: [String: Int]
}

private func fixture() throws -> TokenFixture {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    return try JSONDecoder().decode(TokenFixture.self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/ui/tokens.json")))
}

private func roles(_ c: FoundryColors) -> [String: TokenColor] {
    ["surfaceGround": c.surfaceGround, "surfaceSunk": c.surfaceSunk, "surfacePanel": c.surfacePanel,
     "surfaceRaised": c.surfaceRaised, "ink": c.ink, "inkSecondary": c.inkSecondary, "inkMuted": c.inkMuted,
     "line": c.line, "lineStrong": c.lineStrong, "accent": c.accent, "accentTint": c.accentTint,
     "fill": c.fill, "fillInk": c.fillInk, "info": c.info, "warn": c.warn, "crit": c.crit]
}

@Test func v1MatchesSharedPalettesAndNativeScales() throws {
    let f = try fixture()
    for appearance in FoundryAppearance.allCases {
        let tokens = FoundryPreset.v1(appearance: appearance)
        let expected = try #require(f.presets["v1"]?[appearance.rawValue])
        let actual = roles(tokens.colors)
        #expect(Set(actual.keys) == Set(expected.keys))
        for (name, color) in actual {
            let value = try #require(expected[name])
            #expect(color.hex == "#" + value.rgb)
            #expect(abs(color.alpha - value.alpha) < 0.00001)
        }
        #expect(tokens.space.steps.map(Int.init) == f.space)
        #expect(tokens.shape.radii.map(Int.init) == f.radii)
        #expect(Int(tokens.shape.minimumInteractive) == f.minimumInteractive["swift"])
        #expect(tokens.space.inline == 8 && tokens.space.stack == 12 && tokens.space.section == 24 && tokens.space.page == 20)
    }
}

private func luminance(_ color: TokenColor) -> Double {
    func linear(_ value: UInt32) -> Double {
        let c = Double(value) / 255
        return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear((color.rgb >> 16) & 255) + 0.7152 * linear((color.rgb >> 8) & 255) + 0.0722 * linear(color.rgb & 255)
}

@Test func prescribedTextPairsHaveMinimumContrast() {
    for appearance in FoundryAppearance.allCases {
        let c = FoundryPreset.v1(appearance: appearance).colors
        let inverse = FoundryPreset.v1(appearance: appearance == .dark ? .light : .dark).colors
        let pairs = [(c.ink, c.surfaceGround), (c.ink, c.surfacePanel),
                     (c.inkSecondary, c.surfaceGround), (c.inkSecondary, c.surfacePanel),
                     (c.inkMuted, c.surfaceGround), (c.inkMuted, c.surfacePanel),
                     (c.accent, c.surfacePanel), (c.fillInk, c.fill),
                     (c.info, c.surfacePanel), (c.warn, c.surfacePanel), (c.crit, c.surfacePanel),
                     (inverse.accent, c.ink)]
        for (foreground, background) in pairs {
            #expect(foreground.alpha == 1 && background.alpha == 1)
            let a = luminance(foreground), b = luminance(background)
            #expect((max(a, b) + 0.05) / (min(a, b) + 0.05) >= 4.5)
        }
    }
}

@Test func reducedMotionZeroesAllCustomDurationsWithoutChangingPalette() throws {
    let f = try fixture()
    for appearance in FoundryAppearance.allCases {
        let normal = FoundryPreset.v1(appearance: appearance)
        let reduced = FoundryPreset.v1(appearance: appearance, reduceMotion: true)
        #expect(normal.motion.milliseconds == f.motionMilliseconds)
        #expect(reduced.motion.milliseconds == [0, 0, 0])
        #expect(reduced.colors == normal.colors)
    }
}

@Test func materialThemesMatchSharedRolesWithoutChangingColorOrMotion() throws {
    struct Case: Decodable {
        let style: String
        let reduceTransparency: Bool
        let content: String
        let floating: String
    }
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let cases = try JSONDecoder().decode([Case].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/ui/materials.json")))
    for appearance in FoundryAppearance.allCases {
        let baseline = FoundryPreset.v1(appearance: appearance)
        for row in cases {
            let style = try #require(FoundryThemeStyle(rawValue: row.style))
            let tokens = FoundryPreset.v1(appearance: appearance, style: style, reduceTransparency: row.reduceTransparency)
            #expect(tokens.materials.content.rawValue == row.content)
            #expect(tokens.materials.floating.rawValue == row.floating)
            #expect(tokens.materials.style == style)
            #expect(tokens.colors == baseline.colors)
            #expect(tokens.motion.milliseconds == baseline.motion.milliseconds)
        }
    }
}
