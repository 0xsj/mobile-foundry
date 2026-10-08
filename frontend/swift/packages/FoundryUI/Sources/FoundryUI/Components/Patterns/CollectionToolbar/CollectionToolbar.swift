import SwiftUI

/// Native slots for collection controls. The caller owns filtering, sorting, selection and summary copy.
public struct CollectionToolbar<Filters: View, Actions: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let summary: String?
    private let filters: Filters
    private let actions: Actions
    public init(_ title: String, summary: String? = nil,
                @ViewBuilder filters: () -> Filters, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.summary = summary; self.filters = filters(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.stack) {
            Text(title).font(tokens.typography.heading).accessibilityAddTraits(.isHeader)
            if let summary { Text(summary).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color) }
            filters
            actions
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension CollectionToolbar where Filters == EmptyView {
    public init(_ title: String, summary: String? = nil, @ViewBuilder actions: () -> Actions) {
        self.init(title, summary: summary, filters: { EmptyView() }, actions: actions)
    }
}
extension CollectionToolbar where Filters == EmptyView, Actions == EmptyView {
    public init(_ title: String, summary: String? = nil) {
        self.init(title, summary: summary, filters: { EmptyView() }, actions: { EmptyView() })
    }
}
