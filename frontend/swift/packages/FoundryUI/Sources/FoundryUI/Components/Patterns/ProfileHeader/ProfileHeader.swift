import SwiftUI

/// Supplied identity copy, decorative avatar and independent status/actions. No account or image ownership.
public struct ProfileHeader<AvatarContent: View, Status: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var textSize
    private let title: String
    private let detail: String?
    private let avatar: AvatarContent
    private let status: Status
    private let actions: Actions
    public init(_ title: String, detail: String? = nil, @ViewBuilder avatar: () -> AvatarContent,
                @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.detail = detail; self.avatar = avatar(); self.status = status(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            if textSize.isAccessibilitySize {
                avatar.accessibilityHidden(true)
                copy
            } else {
                HStack(alignment: .center, spacing: t.space.stack) { avatar.accessibilityHidden(true); copy }
            }
            status
            actions
        }.frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(t.colors.ink.color)
    }
    private var copy: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.heading).fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
            if let detail { Text(detail).font(t.typography.body).foregroundStyle(t.colors.inkSecondary.color).fixedSize(horizontal: false, vertical: true) }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
