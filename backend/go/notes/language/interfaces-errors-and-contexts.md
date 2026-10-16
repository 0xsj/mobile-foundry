# Interfaces, errors and contexts in Go

Small interfaces express the capability a caller needs; ordinary error returns
and explicit contexts carry failure and cancellation without an effect runtime.

Origin: runtime batch, 2026-10-12, source inspection and executed Go 1.27.1 tests.
Primary references: [interfaces](https://go.dev/ref/spec#Interface_types),
[errors](https://pkg.go.dev/errors), [context](https://pkg.go.dev/context).

## What and why

A type implements an interface by its method set. There is no implements clause.
A function type can have a method too: ProbeFunc wraps an injected function and
implements the Readiness interface. Value receivers copy their receiver; pointer
receivers operate on the pointed-to instance. Keep mutable lifecycle owners as
pointers and immutable scalar values detached.

An error interface containing a nil pointer is still a nonnil interface. Test
for missing dependencies at composition and ensure adapters do not return typed
nil errors. The public failure boundary treats a typed-nil classified error as
internal. Constructors do not use reflection to detect every typed-nil dependency.

Wrapping with `%w` retains a cause. `errors.Is` matches conditions through wrapping;
`errors.As` finds a compatible concrete type. Matching error text is fragile.
Joined failures represent several errors, so public projection cannot assume the
first matching condition describes the entire failure.

Context is passed explicitly as the first operation parameter. Cancellation is
cooperative; call cancellation functions to release timer resources. Shutdown
needs its own bounded context because the process context is already canceled.
Stopping a read does not prove an accepted remote write was rolled back.

## Example

Conceptual:

```go
// Conceptual: consumers own the required interface.
type Reader interface {
    Find(ctx context.Context, key string) (Record, bool, error)
}
```

Successful absence is `found=false, err=nil`; a failed dependency is an error.
The Record and provider are deliberately omitted. A future application should
define its own narrower domain inputs rather than reuse this sketch verbatim.

## Gotchas and use

Interfaces are seams, not proof of provider equivalence. Cancellation, write
atomicity, error categories and scope must be specified and checked separately.
Used for clocks, ID generation, credential resolution and readiness queries in
this runtime. Tests cover classified wrapping, typed nil, context cancellation
and query failure; this conceptual example was not compiled as a standalone app.

Related: [module assembly](../modules/pkg/module/README.md),
[errors](../modules/pkg/errors/README.md), and
[ports/composition](../patterns/ports-and-module-composition.md).
