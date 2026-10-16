import FoundryUI
import SwiftUI

/// Page presentation is product-owned; each feature can replace this placeholder.
struct ProductPage<Content: View>: View {
    @Environment(\.foundry) private var t
    let title: String
    let subtitle: String
    let symbol: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                SectionHeader(title, subtitle: subtitle)
                Card(.floating) {
                    Image(systemName: symbol).font(.largeTitle).foregroundStyle(t.colors.accent.color).accessibilityHidden(true)
                    Text("Make this space your own.").font(t.typography.heading)
                    content()
                }
            }.padding(t.space.page)
        }
    }
}
