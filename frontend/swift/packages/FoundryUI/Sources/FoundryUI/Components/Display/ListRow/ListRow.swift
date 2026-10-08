import SwiftUI

/// Layout only. Wrap in a native Button or NavigationLink when the whole row is actionable.
public struct ListRow<Leading: View, Trailing: View>: View {
    @Environment(\.foundry) private var tokens
    @Environment(\.dynamicTypeSize) private var typeSize
    private let title: String
    private let subtitle: String?
    private let leading: Leading
    private let trailing: Trailing
    public init(_ title: String, subtitle: String? = nil,
                @ViewBuilder leading: () -> Leading, @ViewBuilder trailing: () -> Trailing) {
        self.title = title; self.subtitle = subtitle; self.leading = leading(); self.trailing = trailing()
    }
    public var body: some View {
        HStack(alignment: .center, spacing: tokens.space.stack) {
            leading
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: tokens.space.inline) { copy; trailing }
            } else {
                copy
                Spacer(minLength: tokens.space.inline)
                trailing
            }
        }.frame(maxWidth: .infinity, minHeight: tokens.shape.minimumInteractive, alignment: .leading)
    }
    private var copy: some View {
        VStack(alignment: .leading, spacing: tokens.space.steps[1]) {
            Text(title).font(tokens.typography.label)
            if let subtitle { Text(subtitle).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color) }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension ListRow where Leading == EmptyView, Trailing == EmptyView {
    public init(_ title: String, subtitle: String? = nil) {
        self.init(title, subtitle: subtitle, leading: { EmptyView() }, trailing: { EmptyView() })
    }
}
