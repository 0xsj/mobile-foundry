import SwiftUI

/// Passive supplied identity with independent access and action slots. No role, membership or invitation policy.
public struct MemberRow<Avatar: View, Access: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var typeSize
    private let name: String
    private let detail: String?
    private let accessibilityLabel: String
    private let avatar: Avatar
    private let access: Access
    private let actions: Actions
    public init(_ name: String, detail: String? = nil, accessibilityLabel: String,
                @ViewBuilder avatar: () -> Avatar, @ViewBuilder access: () -> Access, @ViewBuilder actions: () -> Actions) {
        self.name = name; self.detail = detail; self.accessibilityLabel = accessibilityLabel
        self.avatar = avatar(); self.access = access(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Group {
                if typeSize.isAccessibilitySize { VStack(alignment: .leading, spacing: t.space.inline) { avatar; identity } }
                else { HStack(alignment: .top, spacing: t.space.inline) { avatar; identity } }
            }.accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel)
            access
            actions
        }.frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(t.colors.ink.color)
    }
    private var identity: some View {
        VStack(alignment: .leading, spacing: t.space.steps[1]) {
            Text(name).font(t.typography.label)
            if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
