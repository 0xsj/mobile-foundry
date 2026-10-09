import SwiftUI

public struct TabItem<Icon: View>: Identifiable {
    public let id: String
    public let label: String
    fileprivate let icon: Icon
    public init(id: String, label: String, @ViewBuilder icon: () -> Icon) { self.id = id; self.label = label; self.icon = icon() }
}

/// Controlled native TabView adapter. Native chrome owns safe areas/material/accessibility; host owns routes and selected identity.
public struct TabBar<Icon: View, Content: View>: View {
    private let items: [TabItem<Icon>]
    @Binding private var selectedId: String
    private let content: (String) -> Content
    public init(items: [TabItem<Icon>], selectedId: Binding<String>, @ViewBuilder content: @escaping (String) -> Content) {
        precondition((1...5).contains(items.count) && items.allSatisfy { !$0.id.isEmpty } && Set(items.map(\.id)).count == items.count)
        precondition(items.contains { $0.id == selectedId.wrappedValue })
        self.items = items; self._selectedId = selectedId; self.content = content
    }
    public var body: some View {
        TabView(selection: Binding(get: { selectedId }, set: { value in
            if value != selectedId && items.contains(where: { $0.id == value }) { selectedId = value }
        })) {
            ForEach(items) { item in
                content(item.id).tabItem {
                    Label { Text(item.label) } icon: { item.icon }.environment(\.symbolVariants, .none)
                }.tag(item.id)
            }
        }
    }
}
