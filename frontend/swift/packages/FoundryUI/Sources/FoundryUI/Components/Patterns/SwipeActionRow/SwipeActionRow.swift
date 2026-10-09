import SwiftUI

public struct SwipeAction {
    public let title: String
    public let destructive: Bool
    public let enabled: Bool
    public let onPerform: () -> Void
    public init(_ title: String, destructive: Bool = false, enabled: Bool = true, onPerform: @escaping () -> Void) {
        self.title = title; self.destructive = destructive; self.enabled = enabled; self.onPerform = onPerform
    }
}
/// Native List-row swipe actions, one per logical edge. Host owns identity, effects, confirmation and undo.
public struct SwipeActionRow<Content: View>: View {
    @Environment(\.foundry) private var t
    private let leading: SwipeAction?
    private let trailing: SwipeAction?
    private let enabled: Bool
    private let content: Content
    public init(leading: SwipeAction? = nil, trailing: SwipeAction? = nil, enabled: Bool = true,
                @ViewBuilder content: () -> Content) {
        self.leading = leading; self.trailing = trailing; self.enabled = enabled; self.content = content()
    }
    public var body: some View {
        content.swipeActions(edge: .leading, allowsFullSwipe: true) {
            if let leading, enabled && leading.enabled { action(leading) }
        }.swipeActions(edge: .trailing, allowsFullSwipe: true) {
            if let trailing, enabled && trailing.enabled { action(trailing) }
        }
    }
    private func action(_ value: SwipeAction) -> some View {
        Button(value.title, role: value.destructive ? .destructive : nil, action: value.onPerform)
            .tint(value.destructive ? t.colors.crit.color : t.colors.accent.color)
    }
}
