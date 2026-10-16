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
            DetailShell(header: {
                VStack(alignment: .leading, spacing: t.space.inline) {
                    SectionHeader(configuration.displayName, subtitle: ProductState.shellTitle)
                    ToggleField("Glass", isOn: $state.glass)
                }.padding(.horizontal, t.space.page).padding(.vertical, t.space.inline)
            }, content: { ProductContent(state: $state) }, actions: { EmptyView() })
        })
    }
}
