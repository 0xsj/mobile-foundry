import SwiftUI

public enum MessageTone: String, CaseIterable, Sendable {
    case neutral, info, warning, critical
    public func color(in tokens: FoundryTokens) -> Color {
        switch self {
        case .neutral: tokens.colors.inkSecondary.color
        case .info: tokens.colors.info.color
        case .warning: tokens.colors.warn.color
        case .critical: tokens.colors.crit.color
        }
    }
}

/// Persistent inline feedback. Recovery actions and dismissal are caller-owned slots.
public struct InlineAlert<Actions: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let message: String
    private let tone: MessageTone
    private let actions: Actions
    public init(_ title: String, message: String, tone: MessageTone = .info,
                @ViewBuilder actions: () -> Actions) {
        self.title = title; self.message = message; self.tone = tone; self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.inline) {
            Label(title, systemImage: tone == .critical || tone == .warning ? "exclamationmark.triangle" : "info.circle")
                .font(tokens.typography.label).foregroundStyle(tone.color(in: tokens))
            Text(message).font(tokens.typography.body)
            actions
        }.frame(maxWidth: .infinity, alignment: .leading).padding(tokens.space.stack)
            .background(tokens.colors.surfacePanel.color, in: RoundedRectangle(cornerRadius: tokens.shape.radii[2]))
            .overlay(RoundedRectangle(cornerRadius: tokens.shape.radii[2]).stroke(tone.color(in: tokens)))
    }
}

extension InlineAlert where Actions == EmptyView {
    public init(_ title: String, message: String, tone: MessageTone = .info) {
        self.init(title, message: message, tone: tone) { EmptyView() }
    }
}
