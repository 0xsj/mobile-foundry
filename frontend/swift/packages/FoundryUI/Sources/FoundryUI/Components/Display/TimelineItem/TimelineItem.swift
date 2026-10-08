import SwiftUI

/// A passive timeline marker and connector alongside supplied content. Ordering, timestamps and actions belong to the caller.
public struct TimelineItem<Content: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let timestamp: String
    private let showsConnector: Bool
    private let content: Content
    public init(_ title: String, timestamp: String, showsConnector: Bool = true, @ViewBuilder content: () -> Content) {
        self.title = title; self.timestamp = timestamp; self.showsConnector = showsConnector; self.content = content()
    }
    public var body: some View {
        HStack(alignment: .top, spacing: t.space.inline) {
            VStack(spacing: 8) {
                Circle().fill(t.colors.accent.color).frame(width: 8, height: 8)
                if showsConnector { Rectangle().fill(t.colors.line.color).frame(width: 1).frame(maxHeight: .infinity) }
            }.padding(.top, 8).frame(width: 16).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: t.space.inline) {
                Text(title).font(t.typography.label)
                Text(timestamp).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                content
            }.frame(maxWidth: .infinity, alignment: .leading).padding(.bottom, t.space.inline)
        }.fixedSize(horizontal: false, vertical: true)
    }
}
