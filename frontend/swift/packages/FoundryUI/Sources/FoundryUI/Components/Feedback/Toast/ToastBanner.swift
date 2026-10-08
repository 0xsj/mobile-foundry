import SwiftUI

public struct ToastAction {
    public let title: String
    public let onPerform: () -> Void
    public init(_ title: String, onPerform: @escaping () -> Void) { self.title = title; self.onPerform = onPerform }
}

/// Caller-presented feedback. Queueing, timeouts, announcements and removal policy stay with the host.
public struct ToastBanner: View {
    @Environment(\.foundry) private var tokens
    private let message: String
    private let tone: MessageTone
    private let action: ToastAction?
    private let dismissLabel: String
    private let onDismiss: () -> Void
    public init(_ message: String, tone: MessageTone = .info, action: ToastAction? = nil,
                dismissLabel: String, onDismiss: @escaping () -> Void) {
        self.message = message; self.tone = tone; self.action = action; self.dismissLabel = dismissLabel; self.onDismiss = onDismiss
    }
    public var body: some View {
        Card(.floating) {
            Label(message, systemImage: tone == .critical || tone == .warning ? "exclamationmark.circle" : "info.circle")
                .font(tokens.typography.body).foregroundStyle(tone.color(in: tokens))
            ViewThatFits(in: .horizontal) {
                HStack { actions }
                VStack(alignment: .leading) { actions }
            }
        }
    }
    @ViewBuilder private var actions: some View {
        if let action { ActionButton(action.title, variant: .secondary, action: action.onPerform) }
        ActionButton(dismissLabel, variant: .quiet, action: onDismiss)
    }
}
