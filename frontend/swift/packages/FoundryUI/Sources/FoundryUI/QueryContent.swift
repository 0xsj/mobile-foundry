import SwiftUI
import FoundryKernel
import FoundryQuery

/// Caller-selected copy; pass localized strings from the owning application.
public struct QueryCopy: Sendable {
    public let idle: String
    public let loading: String
    public let refreshing: String
    public let empty: String
    public let refresh: String
    public let retry: String
    public let cancel: String

    public init(idle: String, loading: String, refreshing: String, empty: String,
                refresh: String = "Refresh", retry: String = "Retry", cancel: String = "Cancel loading") {
        self.idle = idle
        self.loading = loading
        self.refreshing = refreshing
        self.empty = empty
        self.refresh = refresh
        self.retry = retry
        self.cancel = cancel
    }
}

/// Rows suitable for a List/Section or stack. The host owns scrolling and effects.
public struct QueryContent<Value: Sendable, Content: View>: View {
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
            case .idle: Text(copy.idle)
            case .loading(let previous):
                ProgressView(previous == nil ? copy.loading : copy.refreshing)
                if let previous { snapshot(previous) }
                Button(copy.cancel, action: cancel)
            case .loaded(let value): snapshot(value)
            case .failed(let failure, let previous):
                Text(failure.publicInfo().meta.message)
                if let previous { snapshot(previous) }
                Button(copy.retry, action: refresh)
            }
            Button(copy.refresh, action: refresh)
        }
    }

    @ViewBuilder private func snapshot(_ value: Value) -> some View {
        if isEmpty(value) { Text(copy.empty) }
        else { content(value) }
    }
}
