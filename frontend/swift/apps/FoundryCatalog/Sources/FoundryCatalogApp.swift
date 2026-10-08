import FoundryUI
import SwiftUI

@main
struct FoundryCatalogApp: App {
    @State private var glassTheme = false
    var body: some Scene {
        WindowGroup {
            FoundryTheme(style: glassTheme ? .glass : .solid) { CatalogView(glassTheme: $glassTheme) }
        }
    }
}
