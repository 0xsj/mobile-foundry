import SwiftUI

public enum CheckState: String, CaseIterable, Sendable { case off, on, mixed }

/// An independent or aggregate choice. The caller determines the next state, including mixed → on.
public struct Checkbox: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let state: CheckState
    private let stateDescription: String
    private let enabled: Bool
    private let onToggle: () -> Void
    public init(_ title: String, state: CheckState, stateDescription: String,
                enabled: Bool = true, onToggle: @escaping () -> Void) {
        self.title = title; self.state = state; self.stateDescription = stateDescription
        self.enabled = enabled; self.onToggle = onToggle
    }
    public var body: some View {
        Button(action: onToggle) {
            HStack(spacing: tokens.space.stack) {
                Image(systemName: state == .on ? "checkmark.square" : state == .mixed ? "minus.square" : "square")
                    .font(.title3).foregroundStyle(tokens.colors.accent.color).accessibilityHidden(true)
                Text(title).font(tokens.typography.body)
            }.frame(maxWidth: .infinity, minHeight: tokens.shape.minimumInteractive, alignment: .leading)
                .contentShape(Rectangle())
        }.buttonStyle(.plain).disabled(!enabled).opacity(enabled ? 1 : 0.5)
            .accessibilityLabel(title).accessibilityValue(stateDescription)
            .accessibilityAddTraits(state == .on ? .isSelected : [])
    }
}
