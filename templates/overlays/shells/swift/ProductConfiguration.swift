import Foundation

/// Installed branding belongs to the app, independently of library/target names.
struct ProductConfiguration {
    let displayName: String
    static let defaultGlass = true
    static var current: Self {
        Self(displayName: Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "My Product")
    }
}
