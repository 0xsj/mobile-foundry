import FoundryUI
import SwiftUI

struct ProductShellCatalogView: View {
    @State private var values = ProductShellState()
    @State private var glass = true
    @Environment(\.foundry) private var t
    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                SectionHeader("Start with a shape", subtitle: "Five working layouts for common products. Each preview keeps its own local state.")
                ForEach(ProductShellPattern.allCases) { pattern in
                    Card(.floating) {
                        Image(systemName: pattern.symbol).font(.title2).foregroundStyle(t.colors.accent.color).accessibilityHidden(true)
                        NavLink("Open \(pattern.title)", subtitle: pattern.subtitle) {
                            ProductShellPreview(pattern: pattern, values: $values, glass: $glass)
                        }
                    }
                }
            }.padding(t.space.page)
        }.navigationTitle("Product shells").navigationBarTitleDisplayMode(.inline)
    }
}
struct ProductShellPreview: View {
    let pattern: ProductShellPattern
    @Binding var values: ProductShellState
    @Binding var glass: Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.foundry) private var incoming
    var body: some View {
        FoundryTheme(appearance: incoming.appearance, style: glass ? .glass : .solid) {
            ProductShellViewport(pattern: pattern, values: $values, glass: $glass, onClose: { dismiss() })
        }.toolbar(.hidden, for: .navigationBar)
    }
}
struct ProductShellViewport: View {
    let pattern: ProductShellPattern
    @Binding var values: ProductShellState
    @Binding var glass: Bool
    let onClose: () -> Void
    @Environment(\.foundry) private var t
    var body: some View {
        AppShell(background: {
            LinearGradient(colors: [t.colors.surfaceGround.color, t.colors.accentTint.color.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
        }, content: {
            ProductShellLayout(pattern: pattern, values: $values)
                .safeAreaInset(edge: .top, spacing: 0) {
                HStack {
                    ActionButton("Back to shells", variant: .quiet, action: onClose)
                    Spacer(minLength: t.space.inline)
                    Toggle("Glass", isOn: $glass).font(t.typography.caption).fixedSize()
                }.padding(.horizontal, t.space.page).padding(.vertical, t.space.inline)
                    .fixedSize(horizontal: false, vertical: true)
                }
        })
    }
}
