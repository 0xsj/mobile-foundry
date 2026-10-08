import SwiftUI

/// Passive avatar artwork, read as one caller-supplied summary. Stable identity and localized overflow copy are caller-owned.
public struct AvatarGroup<Item: Identifiable, Content: View>: View {
    private let items: [Item]
    private let label: String
    private let overflowText: String
    private let maximumVisible: Int
    private let size: CGFloat
    private let avatar: (Item) -> Content
    public init(_ items: [Item], label: String, overflowText: String, maximumVisible: Int = 4,
                size: CGFloat = 40, @ViewBuilder avatar: @escaping (Item) -> Content) {
        precondition(maximumVisible > 0 && size.isFinite && size > 0)
        self.items = items; self.label = label; self.overflowText = overflowText
        self.maximumVisible = maximumVisible; self.size = size; self.avatar = avatar
    }
    public var body: some View {
        HStack(spacing: -size / 5) {
            ForEach(Array(items.prefix(maximumVisible))) { item in avatar(item).frame(width: size, height: size) }
            if items.count > maximumVisible { Avatar(overflowText, fallback: overflowText, size: size) }
        }.accessibilityElement(children: .ignore).accessibilityLabel(label)
    }
}
