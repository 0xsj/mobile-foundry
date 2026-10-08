import FoundryQuery
import SwiftUI

/// Presentation only. A retry decision belongs to the feature that knows the write.
public struct MutationFeedback<Value: Sendable, Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let state: MutationState<Value>
    private let submitting: String
    private let success: (Value) -> Content

    public init(_ state: MutationState<Value>, submitting: String, @ViewBuilder success: @escaping (Value) -> Content) {
        self.state = state; self.submitting = submitting; self.success = success
    }

    public var body: some View {
        switch state {
        case .idle: EmptyView()
        case .submitting: Text(submitting).foregroundStyle(tokens.colors.inkSecondary.color)
        case .succeeded(let value): success(value)
        case .failed(let failure):
            Label(failure.publicInfo().meta.message, systemImage: "exclamationmark.circle")
                .foregroundStyle(tokens.colors.crit.color)
        }
    }
}
