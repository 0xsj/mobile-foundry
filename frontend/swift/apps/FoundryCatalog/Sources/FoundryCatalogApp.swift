import FoundryUI
import SwiftUI

@main
struct FoundryCatalogApp: App {
    @UIApplicationDelegateAdaptor(NotificationApplicationDelegate.self) private var notifications
    @State private var glassTheme = true
    var body: some Scene {
        WindowGroup {
            FoundryTheme(style: glassTheme ? .glass : .solid) { AppShellView(glassTheme: $glassTheme) }
        }
    }
}
