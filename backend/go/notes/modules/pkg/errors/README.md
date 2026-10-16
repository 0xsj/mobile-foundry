# Conditions, causes and public failures

A Go error can preserve private diagnostics and stable condition identity while
transport receives only deliberate public information.

Origin: runtime batch, 2026-10-12. Read
[implementation](../../../../pkg/errors/errors.go) and
[privacy/ownership tests](../../../../pkg/errors/errors_test.go).

`New` creates a diagnostic condition. Derivation with `WithPublic`, `WithFields`
or `WithCause` returns a new occurrence and retains its condition identity.
`errors.Is` can match the original condition and unwrap its cause. An unrelated
condition with identical text does not match. Private fields prevent default
JSON marshaling from serializing diagnostic content.

Public projection hides internal/unknown codes, fields and messages. Noninternal
copy requires an explicit public message; arbitrary cause text never becomes
copy. Input maps and projected maps are cloned. An outer classified error owns
its category; ordinary wrapping preserves it. Cancellation/deadline errors have
their own categories. Multi-failure joins cannot claim one component's public condition, and
mixed/unknown joined failures fail closed as internal.
PostgreSQL startup exposed a useful exception: `errors.Join(err, nil)` contains
only one failure, so its deliberate safe projection survives cleanup wrapping.
The regression check exercises that path without exposing its private cause.

`make test` proves field ownership, projection, condition/cause identity, unknown
and typed-nil behavior, context categories and conservative joined errors. Safe
copy remains the owner's responsibility; this package is not a text redactor.
Domain/application conditions will live in their future modules.

Related: [interfaces/errors](../../../language/interfaces-errors-and-contexts.md)
and [HTTP projection](../httpx/README.md). Next: how should a persistence adapter
distinguish a named conflict from an unknown database defect?
