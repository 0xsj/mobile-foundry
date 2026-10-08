import SwiftUI

public struct MenuAction: Identifiable {
    public let id: String
    public let title: String
    public let destructive: Bool
    public let enabled: Bool
    public let onSelect: () -> Void
    public init(id: String, title: String, destructive: Bool = false, enabled: Bool = true, onSelect: @escaping () -> Void) {
        self.id = id; self.title = title; self.destructive = destructive; self.enabled = enabled; self.onSelect = onSelect
    }
}

public struct ActionMenu: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let actions: [MenuAction]
    public init(_ title: String, actions: [MenuAction]) {
        precondition(Set(actions.map(\.id)).count == actions.count)
        self.title = title; self.actions = actions
    }
    public var body: some View {
        Menu(title) {
            ForEach(actions) { action in
                Button(action.title, role: action.destructive ? .destructive : nil, action: action.onSelect)
                    .disabled(!action.enabled)
            }
        }.frame(minHeight: tokens.shape.minimumInteractive).disabled(actions.isEmpty)
    }
}
