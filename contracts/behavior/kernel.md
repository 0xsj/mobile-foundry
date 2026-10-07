# Kernel outcomes and failures

Status: Established for the first native kernel slice, 2026-10-08.

Services and repositories return either a successful value or a typed expected
failure. Legitimate absence is a successful optional value or empty collection.
The kernel has no UI, HTTP, persistence, or provider dependency.

## Outcomes

Swift uses standard `Result<Value, Failure>` through the `AppResult<Value>`
alias. Kotlin uses `Outcome<Value, Problem>` with `Ok` and `Err` variants;
`AppResult<Value>` fixes `Problem` to the foundation `Failure` vocabulary.
Operation-specific error types remain possible in both languages.

`map` transforms only success. `mapError` transforms only failure. `flatMap`
chains only success with the same error type. The inactive callback is never
called; the inactive payload is preserved. None of these operations catches
exceptions. Swift's standard mapping requires non-throwing callbacks; throwing
work belongs in the enclosing operation or adapter. Kotlin permits callbacks
to throw and propagates the original exception unchanged.

## Failure vocabulary

| Stable kind | Swift case | Kotlin variant | Additional payload |
| --- | --- | --- | --- |
| `unauthenticated` | `unauthenticated` | `Unauthenticated` | None |
| `forbidden` | `forbidden` | `Forbidden` | None |
| `rate_limited` | `rateLimited` | `RateLimited` | Optional `RetryAfter` |
| `unavailable` | `unavailable` | `Unavailable` | None |
| `timeout` | `timeout` | `Timeout` | None |
| `canceled` | `canceled` | `Canceled` | None |
| `internal` | `internalError` | `Internal` | None |
| `not_found` | `notFound` | `NotFound` | None |
| `invalid` | `invalid` | `Invalid` | Required field-message map, possibly empty |
| `conflict` | `conflict` | `Conflict` | None |

Every variant requires `FailureMeta`: a caller-selected message, optional
operation code, request ID, and correlation ID. Specific reasons belong in
`code`, such as `identity.email_in_use`; categories describe broad handling.
HTTP status classification belongs to the future HTTP adapter.

Metadata and variant payloads are immutable after construction. Validation
fields are a snapshot of the supplied map, and callers cannot mutate the stored
map. Retry timing uses signed 64-bit milliseconds; its factory returns absence
for a negative value. Zero and the maximum signed 64-bit value are valid.
Retry timing is information, not permission to retry an operation.

## Public projection and diagnostics

`publicInfo` preserves non-internal failure values. For `internal`, it replaces
the message with `An unexpected error occurred.`, removes `code`, and preserves
the request and correlation IDs. It is idempotent. Validation fields and rate
limit timing survive projection in their respective variants.

Failures contain no original exception, stack, arbitrary diagnostic dictionary,
or transport object. Report an original `Error` or `Throwable` separately at
the adapter or reporting boundary. Public projection cannot sanitize arbitrary
text deliberately placed in a non-internal message, code, field, or ID; those
values must already be appropriate for that boundary. UI selects copy by
recognized code or kind rather than displaying unknown exception messages.

## Exceptions and cancellation

Expected negative outcomes are values; unexpected defects remain exceptions.
This slice adds no catch-all exception classifier or retry policy. Swift's
`Error` conformance allows `Failure` to participate in standard `Result`; it
does not make throwing the default service contract.

Structured cancellation propagates normally. The `canceled` kind is available
only for a later boundary that explicitly promises cancellation as a value.
An adapter must not turn task or coroutine cancellation into `internal`,
`unavailable`, or a retryable value. Future asynchronous adapters must test
their concrete cancellation paths.

## Verification and scope

Both implementations read [the same failure fixtures](../fixtures/kernel/failures.tsv).
The fixtures cover every kind, public projection, optional metadata, validation
fields, and known, zero, and unknown retry delays. Native tests also cover
branch selection, successful absence, field-map ownership, retry bounds, and
projection idempotence. Kotlin tests exercise propagation of callback defects
and cancellation exceptions. A Swift compiler example verifies rejection of a
throwing mapping closure; asynchronous cancellation behavior awaits adapters.

Transport decoding, durable encoding, domain-kind narrowing, exception bridges,
clocks, identifiers, and catalog screens are subsequent slices. This contract
does not define a wire format or an offline synchronization guarantee.
