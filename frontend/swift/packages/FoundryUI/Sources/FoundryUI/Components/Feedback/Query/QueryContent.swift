import SwiftUI
import FoundryKernel
import FoundryQuery

/// Rows suitable for a List/Section or stack. The host owns scrolling and effects.
public struct QueryContent<Value: Sendable, Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let state: QueryState<Value>
    private let copy: QueryCopy
    private let isEmpty: (Value) -> Bool
    private let refresh: () -> Void
    private let cancel: () -> Void
    private let content: (Value) -> Content

    public init(state: QueryState<Value>, copy: QueryCopy, isEmpty: @escaping (Value) -> Bool = { _ in false },
                refresh: @escaping () -> Void, cancel: @escaping () -> Void,
                @ViewBuilder content: @escaping (Value) -> Content) {
        self.state = state
        self.copy = copy
        self.isEmpty = isEmpty
        self.refresh = refresh
        self.cancel = cancel
        self.content = content
    }

    public var body: some View {
        Group {
            switch state {
            case .idle: Text(copy.idle).foregroundStyle(tokens.colors.inkSecondary.color)
            case .loading(let previous):
                ProgressView(previous == nil ? copy.loading : copy.refreshing).tint(tokens.colors.accent.color)
                if let previous { snapshot(previous) }
                Button(copy.cancel, action: cancel)
            case .loaded(let value): snapshot(value)
            case .failed(let failure, let previous):
                Text(failure.publicInfo().meta.message).foregroundStyle(tokens.colors.crit.color)
                if let previous { snapshot(previous) }
                Button(copy.retry, action: refresh)
            }
            Button(copy.refresh, action: refresh)
        }
    }

    @ViewBuilder private func snapshot(_ value: Value) -> some View {
        if isEmpty(value) { Text(copy.empty).foregroundStyle(tokens.colors.inkMuted.color) }
        else { content(value) }
    }
}
