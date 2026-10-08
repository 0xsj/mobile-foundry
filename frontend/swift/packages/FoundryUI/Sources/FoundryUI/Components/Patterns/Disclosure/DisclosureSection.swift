import SwiftUI

/// A full-header native action with separately interactive revealed content.
/// Hoist drafts above this view: collapsed content has no promised state lifetime.
public struct DisclosureSection<Content: View>: View {
    @Environment(\.foundry) private var t
    @Binding private var isExpanded: Bool
    private let title: String
    private let subtitle: String?
    private let stateDescription: String
    private let content: Content
    public init(_ title: String, isExpanded: Binding<Bool>, stateDescription: String, subtitle: String? = nil,
                @ViewBuilder content: () -> Content) {
        self.title = title; self._isExpanded = isExpanded; self.stateDescription = stateDescription
        self.subtitle = subtitle; self.content = content()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            Button { isExpanded.toggle() } label: {
                ListRow(title, subtitle: subtitle, leading: { EmptyView() }, trailing: {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down").accessibilityHidden(true)
                }).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityValue(stateDescription)
            if isExpanded { content.transition(.opacity) }
        }.animation(t.motion.standardAnimation, value: isExpanded)
            .transaction { if t.motion.reduced { $0.disablesAnimations = true } }
    }
}
