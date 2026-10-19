import FoundryUI
import FoundrySession
import SwiftUI

struct ProductRootView: View {
    let configuration: ProductConfiguration
    @Binding var state: ProductState
    @State private var model: ConnectedSessionModel?
    @State private var configurationFailure: String?

    var body: some View {
        FoundryTheme(style: state.glass ? .glass : .solid) {
            if let model {
                ConnectedNavigation(configuration: configuration, state: $state, model: model)
            } else {
                Text(configurationFailure ?? "Opening your workspace…")
                    .padding().task {
                        guard model == nil, configurationFailure == nil else { return }
                        switch ConnectionComposition.make() {
                        case .success(let value): model = value; value.restore()
                        case .failure: configurationFailure = "Set a valid HTTPS API base in ConnectionConfiguration. Debug also supports loopback HTTP."
                        }
                    }
            }
        }
    }
}

private struct ConnectedNavigation: View {
    @Environment(\.foundry) private var t
    @Environment(\.scenePhase) private var scenePhase
    let configuration: ProductConfiguration
    @Binding var state: ProductState
    let model: ConnectedSessionModel

    var body: some View {
        AppShell(background: {
            LinearGradient(colors: [t.colors.surfaceGround.color, t.colors.accentTint.color, t.colors.surfaceRaised.color],
                startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea()
        }, content: {
            TabBar(items: ProductTab.allCases.map { tab in
                TabItem(id: tab.rawValue, label: tab.title) { Image(systemName: tab.symbol) }
            }, selectedId: Binding(get: { state.selectedTab.rawValue }, set: { state.select($0) })) { id in
                switch ProductTab(rawValue: id)! {
                case .today: TodayScreen(model: model)
                case .library: LibraryScreen(model: model)
                case .activity: ActivityScreen()
                case .account: AccountScreen(glass: $state.glass, model: model)
                }
            }
        })
        .onChange(of: model.controller.snapshot, initial: true) { _, _ in
            if scenePhase == .active { model.bindCurrentScope() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.controller.expireIfNeeded(); model.bindCurrentScope(force: true) }
            else { model.detach() }
        }
    }
}
