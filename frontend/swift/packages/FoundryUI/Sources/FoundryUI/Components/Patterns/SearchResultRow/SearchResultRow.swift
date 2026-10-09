import SwiftUI

/// One native open action with passive artwork/preview, plus independent sibling actions. No route or result ownership.
public struct SearchResultRow<Leading: View, Preview: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let detail: String?
    private let accessibilityLabel: String
    private let enabled: Bool
    private let onOpen: () -> Void
    private let leading: Leading
    private let preview: Preview
    private let actions: Actions
    public init(_ title: String, detail: String? = nil, accessibilityLabel: String, enabled: Bool = true,
                onOpen: @escaping () -> Void, @ViewBuilder leading: () -> Leading,
                @ViewBuilder preview: () -> Preview, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.detail = detail; self.accessibilityLabel = accessibilityLabel
        self.enabled = enabled; self.onOpen = onOpen; self.leading = leading(); self.preview = preview(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Button { if enabled { onOpen() } } label: {
                VStack(alignment: .leading, spacing: t.space.inline) {
                    ListRow(title, subtitle: detail, leading: { leading.accessibilityHidden(true) }, trailing: { EmptyView() })
                    preview
                }.frame(maxWidth: .infinity, minHeight: t.shape.minimumInteractive, alignment: .leading).contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(t.colors.ink.color).disabled(!enabled)
                .accessibilityElement(children: .combine).accessibilityLabel(accessibilityLabel)
            actions
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
