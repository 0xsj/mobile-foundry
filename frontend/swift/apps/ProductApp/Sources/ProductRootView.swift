import FoundryUI
import SwiftUI

struct ProductRootView: View {
    let configuration: ProductConfiguration
    @Binding var state: ProductState

    var body: some View {
        FoundryTheme(style: state.glass ? .glass : .solid) {
            ProductNavigation(configuration: configuration, state: $state)
        }
    }
}

private struct ProductNavigation: View {
    @Environment(\.foundry) private var t
    let configuration: ProductConfiguration
    @Binding var state: ProductState

    var body: some View {
        AppShell(background: {
            LinearGradient(colors: [t.colors.surfaceGround.color, t.colors.accentTint.color,
                                    t.colors.surfaceRaised.color], startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
        }, content: {
            TabBar(items: ProductTab.allCases.map { tab in
                TabItem(id: tab.rawValue, label: tab.title) { Image(systemName: tab.symbol) }
            }, selectedId: Binding(get: { state.selectedTab.rawValue }, set: { state.select($0) })) { id in
                switch ProductTab(rawValue: id)! {
                case .today: TodayScreen(displayName: configuration.displayName)
                case .library: LibraryScreen()
                case .activity: ActivityScreen()
                case .account: AccountScreen(glass: $state.glass)
                }
            }
        })
    }
}
