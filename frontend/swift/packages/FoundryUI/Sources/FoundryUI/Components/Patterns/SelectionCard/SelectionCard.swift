import SwiftUI

/// A single-choice option. The slot is a label, and must not contain nested controls.
public struct SelectionCard<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let selected: Bool
    private let enabled: Bool
    private let action: () -> Void
    private let content: Content
    public init(selected: Bool, enabled: Bool = true, onSelect: @escaping () -> Void,
                @ViewBuilder content: () -> Content) {
        self.selected = selected; self.enabled = enabled; self.action = onSelect; self.content = content()
    }
    public var body: some View {
        Button(action: action) {
            Card {
                HStack(alignment: .top, spacing: tokens.space.stack) {
                    VStack(alignment: .leading, spacing: tokens.space.inline) { content }.frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: selected ? "checkmark.circle" : "circle")
                        .foregroundStyle(tokens.colors.accent.color).accessibilityHidden(true)
                }
            }
            .overlay(RoundedRectangle(cornerRadius: tokens.shape.panel)
                .stroke(selected ? tokens.colors.accent.color : tokens.colors.lineStrong.color, lineWidth: selected ? 2 : 1))
            .contentShape(RoundedRectangle(cornerRadius: tokens.shape.panel))
        }.buttonStyle(.plain).disabled(!enabled)
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
