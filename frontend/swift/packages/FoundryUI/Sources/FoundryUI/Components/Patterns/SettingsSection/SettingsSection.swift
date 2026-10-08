import SwiftUI

/// Group native controls or rows; the caller owns bindings and separators.
public struct SettingsSection<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let footer: String?
    private let role: SurfaceRole
    private let content: Content
    public init(_ title: String, footer: String? = nil, role: SurfaceRole = .content,
                @ViewBuilder content: () -> Content) {
        self.title = title; self.footer = footer; self.role = role; self.content = content()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.stack) {
            Text(title).font(tokens.typography.label).accessibilityAddTraits(.isHeader)
            Card(role) { content }
            if let footer { Text(footer).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color) }
        }
    }
}
