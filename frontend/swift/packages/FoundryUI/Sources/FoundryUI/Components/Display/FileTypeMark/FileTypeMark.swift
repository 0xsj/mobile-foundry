import SwiftUI

/// Passive caller-supplied format/kind label. No extension parsing, MIME inference or file access.
public struct FileTypeMark: View {
    @Environment(\.foundry) private var t
    private let text: String
    private let accessibilityLabel: String
    public init(_ text: String, accessibilityLabel: String) { self.text = text; self.accessibilityLabel = accessibilityLabel }
    public var body: some View {
        Text(text).font(t.typography.caption.monospaced().weight(.semibold)).foregroundStyle(t.colors.accent.color)
            .padding(t.space.inline).frame(minWidth: 40, minHeight: 40)
            .background(t.colors.accentTint.color, in: RoundedRectangle(cornerRadius: t.shape.radii[1]))
            .overlay(RoundedRectangle(cornerRadius: t.shape.radii[1]).stroke(t.colors.accent.color))
            .accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel)
    }
}
