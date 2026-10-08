import SwiftUI

public enum AvatarShape: Sendable { case circle, rounded }

/// Passive artwork with one accessible label. The caller supplies admitted image content or fallback copy.
public struct Avatar<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let label: String
    private let fallback: String
    private let size: CGFloat
    private let shape: AvatarShape
    private let content: Content?
    public init(_ label: String, fallback: String, size: CGFloat = 48, shape: AvatarShape = .circle,
                @ViewBuilder content: () -> Content) {
        self.init(label, fallback: fallback, size: size, shape: shape, artwork: content())
    }
    fileprivate init(_ label: String, fallback: String, size: CGFloat, shape: AvatarShape, artwork: Content?) {
        precondition(size.isFinite && size > 0)
        self.label = label; self.fallback = fallback; self.size = size; self.shape = shape; self.content = artwork
    }
    public var body: some View {
        ZStack {
            tokens.colors.accentTint.color
            if let content { content }
            else { Text(fallback).font(tokens.typography.label).foregroundStyle(tokens.colors.accent.color).lineLimit(1).minimumScaleFactor(0.5).padding(4) }
        }.frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: shape == .circle ? size / 2 : min(tokens.shape.panel, size / 4)))
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
    }
}

extension Avatar where Content == EmptyView {
    public init(_ label: String, fallback: String, size: CGFloat = 48, shape: AvatarShape = .circle) {
        self.init(label, fallback: fallback, size: size, shape: shape, artwork: nil)
    }
}
