/// A successful value or a typed expected failure at an owned application boundary.
/// Absence can be represented by an optional successful value.
/// Standard Result mapping operations require non-throwing callbacks.
public typealias AppResult<Value> = Result<Value, Failure>
