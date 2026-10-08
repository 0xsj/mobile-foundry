import FoundryGraphics

enum PhotoFilter: String, CaseIterable {
    case natural = "Natural", mono = "Mono", vivid = "Vivid", soft = "Soft"
    var adjustments: ImageAdjustments {
        switch self {
        case .natural: .init()
        case .mono: .init(saturation: 0, vignette: 0.15)
        case .vivid: .init(exposure: 0.15, saturation: 1.45, vignette: 0.2)
        case .soft: .init(exposure: 0.25, saturation: 0.8, vignette: 0.15)
        }
    }
}
