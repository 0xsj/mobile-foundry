import SwiftUI

@main
struct ProductApp: App {
    private let configuration = ProductConfiguration.current

    var body: some Scene {
        WindowGroup { ProductEntry(configuration: configuration) }
    }
}

private struct ProductEntry: View {
    let configuration: ProductConfiguration
    @State private var state = ProductState(glass: ProductConfiguration.defaultGlass)
    var body: some View { ProductRootView(configuration: configuration, state: $state) }
}
