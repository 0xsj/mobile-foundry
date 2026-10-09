import SwiftUI

public enum MessageDirection: Sendable { case incoming, outgoing }

/// Logical leading/trailing placement. Delivery metadata and interactive accessories are caller supplied.
public struct MessageBubble<Accessories: View>: View {
    @Environment(\.foundry) private var t
    private let author: String
    private let text: String
    private let metadata: String
    private let direction: MessageDirection
    private let accessories: Accessories
    public init(_ author: String, text: String, metadata: String, direction: MessageDirection = .incoming,
                @ViewBuilder accessories: () -> Accessories) {
        self.author = author; self.text = text; self.metadata = metadata
        self.direction = direction; self.accessories = accessories()
    }
    public var body: some View {
        HStack(alignment: .top, spacing: 0) {
            if direction == .outgoing { Spacer(minLength: t.space.section) }
            VStack(alignment: .leading, spacing: t.space.inline) {
                Text(author).font(t.typography.label)
                if !text.isEmpty { Text(text).font(t.typography.body).textSelection(.enabled) }
                accessories
                Text(metadata).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(t.space.stack)
                .background(direction == .outgoing ? t.colors.accentTint.color : t.colors.surfacePanel.color,
                            in: RoundedRectangle(cornerRadius: t.shape.panel))
                .overlay(RoundedRectangle(cornerRadius: t.shape.panel).stroke(t.colors.line.color))
            if direction == .incoming { Spacer(minLength: t.space.section) }
        }.foregroundStyle(t.colors.ink.color)
    }
}
extension MessageBubble where Accessories == EmptyView {
    public init(_ author: String, text: String, metadata: String, direction: MessageDirection = .incoming) {
        self.init(author, text: text, metadata: metadata, direction: direction, accessories: { EmptyView() })
    }
}
