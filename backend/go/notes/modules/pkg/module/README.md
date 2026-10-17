# Module assembly and ownership

Explicit builders make adapter selection visible, while an assembly owns the
lifetime of units that successfully transfer their resources.

Origin: 2026-10-12 runtime batch; source inspection and race-enabled tests.
Read [types](../../../../pkg/module/module.go),
[assembly](../../../../pkg/module/assembly.go), and
[tests](../../../../pkg/module/assembly_test.go).

`Builder` is a function type from Runtime to `(Unit, error)`. Unit exports route,
readiness and cleanup functions, not application services. A business module's
`module.go` injects ports; `build.go` selects concrete adapters. The process only
chooses builders. This implements the deliberate parts of
[Bento authoring](../../../../../../../bento/backend/MODULE-AUTHORING.md), without
copying its database baseline or business catalog.

Construction failure releases earlier successful units in reverse order.
A builder returning failure must release its own partial resources. Duplicate
names also fail and clean up the newly returned unit. `sync.Once` makes cleanup
execute once; `errors.Join` retains multiple cleanup errors. Route registration
conflicts are startup failures; the partly filled mux is discarded.

`make check test` verifies cleanup order, one-time cleanup, duplicate ownership,
nil builders, readiness causes/cancellation and route conflicts. Import tests
protect the dependency direction; they do not establish provider behavior or SQL
ownership. Account/Audit now supply business builders.

Follow-up, 2026-10-13: Unit adds Provides/Requires/Subscriptions/Start. Assembly
first builds all units, then registers owned providers/consumers, checks required
capabilities, freezes registries and starts workers. Builder order therefore
does not decide whether a required provider is found. Names must belong to the
registering unit. Failure at any step closes transferred resources. Failed
startup discards the runtime registries rather than reusing partial registration.
Tests verify missing-provider refusal before worker start and reversed builder
order. Remote resolution remains planned.

PostgreSQL follow-up, 2026-10-13: Unit now separates `Stop` from `Close`.
Assembly joins all producers before closing any module resource. Reverse Close
alone could close Audit's pool while Account was still delivering to it.
The lifecycle test observes both Stop calls before either Close call and checks
one-time cleanup. A failed builder still owns its partial resources.

Realtime/identity follow-up, 2026-10-17: Runtime now carries the generic bounded
Signals hub. Module-local adapters choose keys; domains and handlers do not use
Runtime as a service locator. Realtime Stop closes/joins hijacked sockets, and
Identity Stop joins its bounded ephemeral reset worker alongside event delivery.
Both precede any module pool closure. Actual socket and queue tests exercise
shutdown; see [Realtime](../../internal/realtime/README.md) and
[Identity recovery](../../internal/identity/recovery.md).

Related: [ports/composition](../../../patterns/ports-and-module-composition.md),
[language](../../../language/interfaces-errors-and-contexts.md). Next: which
store semantics must conformance tests specify before swapping a provider?
