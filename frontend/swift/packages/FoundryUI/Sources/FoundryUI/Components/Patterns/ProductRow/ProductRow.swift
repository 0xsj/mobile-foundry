import SwiftUI

/// Passive product copy/artwork and price, with independent native status/actions. No product, stock or cart model.
public struct ProductRow<Artwork: View, Price: View, Status: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let detail: String?
    private let artwork: Artwork
    private let price: Price
    private let status: Status
    private let actions: Actions
    public init(_ title: String, detail: String? = nil, @ViewBuilder artwork: () -> Artwork,
                @ViewBuilder price: () -> Price, @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.detail = detail; self.artwork = artwork(); self.price = price(); self.status = status(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            ListRow(title, subtitle: detail, leading: { artwork.accessibilityHidden(true) }, trailing: { EmptyView() })
            price
            status
            actions
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
