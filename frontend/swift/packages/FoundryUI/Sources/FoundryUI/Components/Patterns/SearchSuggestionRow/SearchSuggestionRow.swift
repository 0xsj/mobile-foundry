import SwiftUI

/// One supplied search/history action. Leading content is decorative; query/history ownership stays in the host.
public struct SearchSuggestionRow<Leading: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let detail: String?
    private let accessibilityLabel: String
    private let enabled: Bool
    private let onUse: () -> Void
    private let leading: Leading
    public init(_ title: String, detail: String? = nil, accessibilityLabel: String, enabled: Bool = true,
                onUse: @escaping () -> Void, @ViewBuilder leading: () -> Leading) {
        self.title = title; self.detail = detail; self.accessibilityLabel = accessibilityLabel
        self.enabled = enabled; self.onUse = onUse; self.leading = leading()
    }
    public var body: some View {
        Button { if enabled { onUse() } } label: {
            ListRow(title, subtitle: detail, leading: { leading.accessibilityHidden(true) }, trailing: {
                Image(systemName: "arrow.up.left").flipsForRightToLeftLayoutDirection(true).accessibilityHidden(true)
            }).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(t.colors.ink.color).disabled(!enabled)
            .accessibilityElement(children: .combine).accessibilityLabel(accessibilityLabel)
    }
}
extension SearchSuggestionRow where Leading == EmptyView {
    public init(_ title: String, detail: String? = nil, accessibilityLabel: String, enabled: Bool = true, onUse: @escaping () -> Void) {
        self.init(title, detail: detail, accessibilityLabel: accessibilityLabel, enabled: enabled, onUse: onUse, leading: { EmptyView() })
    }
}
