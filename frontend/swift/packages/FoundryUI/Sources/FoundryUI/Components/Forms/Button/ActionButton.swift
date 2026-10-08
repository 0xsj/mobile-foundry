import SwiftUI

public enum ButtonVariant: String, CaseIterable, Sendable {
    case primary, secondary, quiet, destructive
}

/// Native button semantics with theme styling. Labels may contain text and icons.
public struct ActionButton<Label: View>: View {
    private let variant: ButtonVariant
    private let busy: Bool
    private let enabled: Bool
    private let action: () -> Void
    private let label: Label

    public init(variant: ButtonVariant = .primary, isBusy: Bool = false,
                enabled: Bool = true, action: @escaping () -> Void,
                @ViewBuilder label: () -> Label) {
        self.variant = variant; self.busy = isBusy; self.enabled = enabled
        self.action = action; self.label = label()
    }

    public var body: some View {
        Button(role: variant == .destructive ? .destructive : nil, action: action) {
            HStack {
                if busy { ProgressView().accessibilityHidden(true) }
                label
            }
        }
        .buttonStyle(ActionButtonStyle(variant: variant))
        .disabled(!enabled || busy)
    }
}

extension ActionButton where Label == Text {
    public init(_ title: String, variant: ButtonVariant = .primary,
                isBusy: Bool = false, enabled: Bool = true, action: @escaping () -> Void) {
        self.init(variant: variant, isBusy: isBusy, enabled: enabled, action: action) { Text(title) }
    }
}

/// Also usable directly on an ordinary SwiftUI Button.
public struct ActionButtonStyle: ButtonStyle {
    @Environment(\.foundry) private var tokens
    @Environment(\.isEnabled) private var enabled
    private let variant: ButtonVariant
    public init(variant: ButtonVariant = .primary) { self.variant = variant }

    public func makeBody(configuration: Configuration) -> some View {
        let ink = variant == .primary ? tokens.colors.fillInk.color
            : variant == .destructive ? tokens.colors.crit.color : tokens.colors.accent.color
        configuration.label
            .font(tokens.typography.label)
            .foregroundStyle(ink).tint(ink)
            .padding(.horizontal, tokens.space.stack).padding(.vertical, tokens.space.inline)
            .frame(minWidth: tokens.shape.minimumInteractive, minHeight: tokens.shape.minimumInteractive)
            .background(variant == .primary ? tokens.colors.fill.color
                        : variant == .secondary ? tokens.colors.surfaceSunk.color : .clear,
                        in: RoundedRectangle(cornerRadius: tokens.shape.radii[2]))
            .overlay {
                if variant == .secondary || variant == .destructive {
                    RoundedRectangle(cornerRadius: tokens.shape.radii[2])
                        .stroke(variant == .destructive ? tokens.colors.crit.color : tokens.colors.lineStrong.color)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: tokens.shape.radii[2]))
            .opacity(!enabled ? 0.5 : configuration.isPressed ? 0.75 : 1)
    }
}
