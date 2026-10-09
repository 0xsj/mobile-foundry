import SwiftUI

/// File presentation with independent actions. No file loading, preview route, or transfer operation.
public struct AttachmentRow<Preview: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let detail: String
    private let preview: Preview
    private let actions: Actions
    public init(_ title: String, detail: String, @ViewBuilder preview: () -> Preview, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.detail = detail; self.preview = preview(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            ListRow(title, subtitle: detail, leading: {
                preview.frame(width: 48, height: 48).clipped().accessibilityHidden(true)
            }, trailing: { EmptyView() })
            actions
        }.foregroundStyle(t.colors.ink.color)
    }
}
