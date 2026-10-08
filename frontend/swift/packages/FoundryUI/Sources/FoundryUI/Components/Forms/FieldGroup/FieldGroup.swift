import SwiftUI

/// Groups native fields without merging their focus targets. Validation and per-field error association are caller-owned.
public struct FieldGroup<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let help: String?
    private let error: String?
    private let content: Content
    public init(_ title: String, help: String? = nil, error: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title; self.help = help; self.error = error; self.content = content()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.stack) {
            Text(title).font(tokens.typography.label).accessibilityAddTraits(.isHeader)
            content
            if let error {
                Label(error, systemImage: "exclamationmark.circle").font(tokens.typography.caption).foregroundStyle(tokens.colors.crit.color)
            } else if let help { Text(help).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color) }
        }.accessibilityElement(children: .contain)
    }
}
