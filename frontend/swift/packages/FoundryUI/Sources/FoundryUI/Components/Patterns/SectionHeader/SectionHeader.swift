import SwiftUI

/// A compact section heading with caller-supplied actions. Actions remain separate accessible controls.
public struct SectionHeader<Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let subtitle: String?
    private let actions: Actions
    public init(_ title: String, subtitle: String? = nil, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.subtitle = subtitle; self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.heading).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).foregroundStyle(t.colors.inkSecondary.color) }
            actions
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
extension SectionHeader where Actions == EmptyView {
    public init(_ title: String, subtitle: String? = nil) { self.init(title, subtitle: subtitle) { EmptyView() } }
}
