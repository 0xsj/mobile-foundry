import SwiftUI

/// Native horizontal paging with stable caller IDs. Supply bounded width/height and passive or independently interactive page content.
/// Selection must identify an item. No auto-advance, loading, image admission or route policy is added.
public struct Carousel<Item: Identifiable, Content: View>: View {
    private let items: [Item]
    @Binding private var selection: Item.ID
    private let content: (Item) -> Content
    public init(_ items: [Item], selection: Binding<Item.ID>, @ViewBuilder content: @escaping (Item) -> Content) {
        precondition(!items.isEmpty && Set(items.map(\.id)).count == items.count && items.contains { $0.id == selection.wrappedValue })
        self.items = items; self._selection = selection; self.content = content
    }
    public var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(items) { item in
                    content(item).containerRelativeFrame(.horizontal).frame(maxHeight: .infinity).id(item.id)
                }
            }.scrollTargetLayout()
        }.scrollIndicators(.hidden).scrollTargetBehavior(.paging)
            .scrollPosition(id: Binding<Item.ID?>(get: { selection }, set: { id in
                if let id, items.contains(where: { $0.id == id }) { selection = id }
            }))
    }
}
