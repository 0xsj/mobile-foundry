import SwiftUI
import Foundation

/// sRGB token data. Raw palette values belong in presets, not component code.
public struct TokenColor: Equatable, Sendable {
    public let rgb: UInt32
    public let alpha: Double

    public init(_ rgb: UInt32, alpha: Double = 1) {
        precondition(rgb <= 0xFFFFFF && (0...1).contains(alpha))
        self.rgb = rgb
        self.alpha = alpha
    }

    public var hex: String { String(format: "#%06X", rgb) }
    public var color: Color {
        Color(.sRGB, red: Double((rgb >> 16) & 255) / 255,
              green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255, opacity: alpha)
    }
}

enum Palette {
    static let white: UInt32 = 0xFFFFFF
    static let porcelain50: UInt32 = 0xFAFBFD
    static let porcelain100: UInt32 = 0xF3F4F6
    static let porcelain200: UInt32 = 0xE8EBF0
    static let graphite50: UInt32 = 0xEEF2F8
    static let graphite300: UInt32 = 0xB3BFD1
    static let graphite400: UInt32 = 0x8E9DB3
    static let graphite500: UInt32 = 0x606E82
    static let graphite600: UInt32 = 0x4D5A6C
    static let graphite800: UInt32 = 0x222C3C
    static let graphite850: UInt32 = 0x182132
    static let graphite900: UInt32 = 0x181F2C
    static let graphite950: UInt32 = 0x10151F
    static let graphite1000: UInt32 = 0x0B0F17
    static let cobalt300: UInt32 = 0xA1B5FF
    static let cobalt700: UInt32 = 0x3155C6
    static let cobalt950: UInt32 = 0x10182C
    static let sky300: UInt32 = 0x8FC8E7
    static let sky700: UInt32 = 0x286A8A
    static let ochre300: UInt32 = 0xE9BC74
    static let ochre700: UInt32 = 0x8A570F
    static let vermilion300: UInt32 = 0xF3A197
    static let vermilion700: UInt32 = 0xB54238
}
