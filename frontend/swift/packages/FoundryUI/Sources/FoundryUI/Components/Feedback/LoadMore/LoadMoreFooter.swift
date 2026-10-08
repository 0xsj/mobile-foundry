import SwiftUI

public enum LoadMorePhase: Sendable { case idle, loading, failed, exhausted }

/// Explicit pagination affordance. It never starts work on appearance or owns a cursor, retry policy or task.
public struct LoadMoreFooter: View {
    @Environment(\.foundry) private var t
    private let phase: LoadMorePhase
    private let message: String
    private let actionLabel: String
    private let enabled: Bool
    private let onLoad: () -> Void
    public init(_ phase: LoadMorePhase, message: String, actionLabel: String, enabled: Bool = true, onLoad: @escaping () -> Void) {
        self.phase = phase; self.message = message; self.actionLabel = actionLabel; self.enabled = enabled; self.onLoad = onLoad
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            if phase == .loading { ProgressIndicator(message) }
            else {
                Text(message).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                if phase != .exhausted { ActionButton(actionLabel, variant: .secondary, enabled: enabled, action: onLoad) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
