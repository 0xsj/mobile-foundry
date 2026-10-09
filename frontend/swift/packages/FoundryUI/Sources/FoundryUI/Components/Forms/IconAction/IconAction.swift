import SwiftUI

/// A named native action with passive icon content. The caller supplies a localized label and all effects.
public struct IconAction<Icon: View>: View {
    private let label: String
    private let variant: ButtonVariant
    private let enabled: Bool
    private let action: () -> Void
    private let icon: Icon
    public init(_ label: String, variant: ButtonVariant = .quiet, enabled: Bool = true,
                action: @escaping () -> Void, @ViewBuilder icon: () -> Icon) {
        self.label = label; self.variant = variant; self.enabled = enabled; self.action = action; self.icon = icon()
    }
    public var body: some View {
        ActionButton(variant: variant, enabled: enabled, action: action) { icon.accessibilityHidden(true) }
            .accessibilityLabel(label)
    }
}
