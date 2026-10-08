import FoundryKernel

/// Pure write presentation state. Failed/canceled does not imply a remote rollback.
public enum MutationState<Value: Sendable>: Sendable {
    case idle
    case submitting
    case succeeded(Value)
    case failed(Failure)

    public var isSubmitting: Bool { if case .submitting = self { true } else { false } }
    public var value: Value? { if case .succeeded(let value) = self { value } else { nil } }
    public var failure: Failure? { if case .failed(let failure) = self { failure } else { nil } }
    public func starting() -> Self { .submitting }
    public func settled(with result: AppResult<Value>) -> Self {
        switch result {
        case .success(let value): .succeeded(value)
        case .failure(let failure): .failed(failure)
        }
    }
    public func reset() -> Self { .idle }
}

extension MutationState: Equatable where Value: Equatable {}
