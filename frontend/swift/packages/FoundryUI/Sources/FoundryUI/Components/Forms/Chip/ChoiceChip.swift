import SwiftUI

/// A compact selected/unselected action. The caller coordinates single or multiple choices.
public struct ChoiceChip<Leading: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let selected: Bool
    private let enabled: Bool
    private let onSelect: () -> Void
    private let leading: Leading
    public init(_ title: String, selected: Bool, enabled: Bool = true, onSelect: @escaping () -> Void,
                @ViewBuilder leading: () -> Leading) {
        self.title = title; self.selected = selected; self.enabled = enabled
        self.onSelect = onSelect; self.leading = leading()
    }
    public var body: some View {
        Button(action: onSelect) {
            HStack(spacing: t.space.inline) { leading; Text(title).font(t.typography.label) }
                .padding(.horizontal, t.space.stack).padding(.vertical, t.space.inline)
                .frame(minWidth: t.shape.minimumInteractive, minHeight: t.shape.minimumInteractive)
                .foregroundStyle(selected ? t.colors.accent.color : t.colors.ink.color)
                .background(selected ? t.colors.accentTint.color : t.colors.surfacePanel.color, in: Capsule())
                .overlay { Capsule().stroke(selected ? t.colors.accent.color : t.colors.lineStrong.color) }
                .contentShape(Capsule())
        }.buttonStyle(.plain).disabled(!enabled).opacity(enabled ? 1 : 0.5)
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

extension ChoiceChip where Leading == EmptyView {
    public init(_ title: String, selected: Bool, enabled: Bool = true, onSelect: @escaping () -> Void) {
        self.init(title, selected: selected, enabled: enabled, onSelect: onSelect, leading: { EmptyView() })
    }
}
