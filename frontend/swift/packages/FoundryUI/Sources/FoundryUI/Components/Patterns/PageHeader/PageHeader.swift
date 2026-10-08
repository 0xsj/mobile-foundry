import SwiftUI

public struct PageHeader<Actions: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let subtitle: String?
    private let actions: Actions
    public init(_ title: String, subtitle: String? = nil, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.subtitle = subtitle; self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.stack) {
            Text(title).font(tokens.typography.title).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).foregroundStyle(tokens.colors.inkSecondary.color) }
            actions
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension PageHeader where Actions == EmptyView {
    public init(_ title: String, subtitle: String? = nil) {
        self.init(title, subtitle: subtitle) { EmptyView() }
    }
}
