import SwiftUI

/// One remove action with a supplied accessible label. No selection, timeout or removal mutation.
public struct RemovableChip: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let removeLabel: String
    private let enabled: Bool
    private let onRemove: () -> Void
    public init(_ title: String, removeLabel: String, enabled: Bool = true, onRemove: @escaping () -> Void) {
        self.title = title; self.removeLabel = removeLabel; self.enabled = enabled; self.onRemove = onRemove
    }
    public var body: some View {
        Button(action: onRemove) {
            HStack(spacing: t.space.inline) {
                Text(title).font(t.typography.label).fixedSize(horizontal: false, vertical: true)
                Image(systemName: "xmark").accessibilityHidden(true)
            }.padding(.horizontal, t.space.stack).frame(minHeight: t.shape.minimumInteractive)
                .foregroundStyle(t.colors.accent.color)
                .background(t.colors.accentTint.color, in: RoundedRectangle(cornerRadius: t.shape.radii[2]))
                .overlay(RoundedRectangle(cornerRadius: t.shape.radii[2]).stroke(t.colors.line.color))
                .contentShape(Rectangle())
        }.buttonStyle(.plain).disabled(!enabled).opacity(enabled ? 1 : 0.5).accessibilityLabel(removeLabel)
    }
}
