# Versioned in-process capability invocation

Claim: an in-process call can preserve a remote-compatible failure/data boundary
without giving business code access to the provider's implementation.

Origin/evidence: 2026-10-18 [registry implementation](../../../../pkg/capability/src/lib.rs),
[invocation tests](../../../../pkg/capability/tests/invocation.rs), Identity v1/v2
tests and actual authenticated Account process acceptance. The shared
[boundary policy](../../../../../../contracts/behavior/module-boundaries.md)
defines the architectural intent; remote routing remains unimplemented.

Read Builder registration, Registry requirements/client creation, Invoker, the
bounded Scope adapter, invocation and typed decoding in that order. Builder
refuses duplicate names/caller errors. freeze consumes mutable assembly into an
Arc-backed immutable registry. A client fixes its caller at composition, so a
request cannot nominate a more privileged caller. The process checks its required
credential provider before serving. Business handlers receive consumer-owned
ports; only an infrastructure adapter receives Invoker.

```rust
// Excerpt: composition freezes registrations before constructing a consumer client.
let registry = builder.freeze();
registry.require("identity.credential.resolve.v2", "api")?;
let client = registry.client("api")?;
```

The [process](../../../../cmd/server/src/main.rs) uses this sequence. Requests and
responses remain bounded JSON objects. Invocation checks parent cancellation,
caller policy and a two-second monotonic deadline. Providers receive a scoped
view with the same correlation metadata and an additional budget check. No task
is spawned: timeout drops the owned provider future. A test uses paused Tokio
time plus a Drop guard to observe that cleanup, rather than sleeping two seconds.

Failures are projected before returning across the seam. An internal provider
diagnostic/code/cause is discarded even though the call stayed in one process.
The consumer's typed decoder then checks exact fields and semantic identifiers.
Identity's custom credential DTO preserves ordered string duplicates/null rules;
generic decoding alone is not a full Go JSON emulation layer.

Gotchas: cooperative checks cannot preempt synchronous CPU work. Dropping a
future is not proof that a write rolled back. There is no retry, remote transport,
transaction across modules, event dispatch or global application service locator.
Local retained facts are a different mechanism from invocation responses.
Run `make rust-test` and `make backend-account-parity-test` for current evidence.

Used by [Account's credential adapter](../../internal/account/README.md) and
[Identity](../../internal/identity/README.md). Related:
[immutable assembly mechanics](../../../language/consuming-builders-and-immutable-registries.md).
Next: how will a remote adapter prove the same caller and failure rules?

Dispatch follow-up: identity.session.eligible.v1 is admitted only for Notifications,
including when Identity and Notifications use separate databases. An API caller
cannot invoke it. Twenty request and fourteen response cases preserve ordered
DTO behavior and required boolean answers; a paused provider test observes the
two-second deadline and dropped future before any gateway call. Read
[the dispatch walkthrough](../../internal/notifications/push.md).
