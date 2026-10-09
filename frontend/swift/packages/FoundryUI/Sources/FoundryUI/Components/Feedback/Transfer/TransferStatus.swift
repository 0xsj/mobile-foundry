import SwiftUI

public enum TransferPhase: Sendable { case waiting, transferring, paused, failed, complete }

/// A projection of an external transfer. Only transferring displays progress; actions never start work here.
public struct TransferStatus<Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let status: String
    private let phase: TransferPhase
    private let fraction: Double?
    private let actions: Actions
    public init(_ title: String, status: String, phase: TransferPhase, fraction: Double? = nil,
                @ViewBuilder actions: () -> Actions) {
        self.title = title; self.status = status; self.phase = phase; self.fraction = fraction; self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            if phase == .transferring { ProgressIndicator(title, fraction: fraction) }
            else { Text(title).font(t.typography.label) }
            Label(status, systemImage: symbol).font(t.typography.caption)
                .foregroundStyle(phase == .failed ? t.colors.crit.color : t.colors.inkSecondary.color)
                .accessibilityLabel(status)
            actions
        }
    }
    private var symbol: String {
        switch phase {
        case .waiting: "clock"
        case .transferring: "arrow.up.circle"
        case .paused: "pause.circle"
        case .failed: "exclamationmark.circle"
        case .complete: "checkmark.circle"
        }
    }
}
extension TransferStatus where Actions == EmptyView {
    public init(_ title: String, status: String, phase: TransferPhase, fraction: Double? = nil) {
        self.init(title, status: status, phase: phase, fraction: fraction, actions: { EmptyView() })
    }
}
