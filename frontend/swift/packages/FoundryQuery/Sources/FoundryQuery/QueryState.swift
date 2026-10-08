import FoundryKernel

/// An immutable query snapshot, independent of observation and request lifetime.
/// Payloads must be immutable snapshots; this container does not copy references.
public enum QueryState<Value: Sendable>: Sendable {
    case idle
    case loading(previous: Value?)
    case loaded(Value)
    case failed(Failure, previous: Value?)

    /// The current or last successful snapshot. Empty values remain present.
    public var value: Value? {
        switch self {
        case .idle: nil
        case .loading(let previous), .failed(_, let previous): previous
        case .loaded(let value): value
        }
    }

    public var failure: Failure? {
        if case .failed(let failure, _) = self { failure } else { nil }
    }

    public var isLoading: Bool {
        if case .loading = self { true } else { false }
    }

    /// Returns loading state; the caller still owns starting and admitting work.
    public func starting() -> Self { .loading(previous: value) }

    /// Apply only an admitted result. This method does not reject stale requests.
    public func settled(with result: AppResult<Value>) -> Self {
        switch result {
        case .success(let value): .loaded(value)
        case .failure(let failure): .failed(failure, previous: value)
        }
    }

    /// Drop progress/failure while retaining the last successful snapshot.
    public func restored() -> Self { value.map(Self.loaded) ?? .idle }
}

extension QueryState: Equatable where Value: Equatable {}
