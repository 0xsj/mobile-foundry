# Personal mappings and write-time compare-and-swap

Claim: repeatable bootstrap and optimistic rename solve different concurrency
problems and should retain different state and acceptance rules.

Origin/evidence: 2026-10-18 Account application/store tests and real Go/Rust
concurrent HTTP checks. Read [the application](../../internal/account/src/app/command/mod.rs)
with [the memory adapter](../../internal/account/src/infra/memory.rs).

Bootstrap proposes a new aggregate, but the store serializes a separate owner to
personal-account mapping. The first commit wins; later calls return that mapped
aggregate's current version. A proposed name never renames it. Keeping the mapping
explicit prevents an unrelated ordinary create from accidentally becoming personal.

Rename instead observes one aggregate/version. Application validation gives a
useful early failure, but the authoritative version comparison belongs under
the write lock, beside aggregate/fact mutation. Otherwise two readers can both
observe version two and overwrite each other's version-three result.

```rust
// Excerpt: the final version check runs with owned state locked, before mutation.
if prior.version != expected {
    return Err(app::conflict());
}
```

The store tests synchronize competing tasks and verify one winner plus exactly
one additional fact. Fact-allocation failure leaves both aggregate and personal
mapping untouched. Public process acceptance observes the same one-winner status
and performs explicit readback. Names are omitted from required facts.

Gotchas: a bootstrap repeat still validates its input/dependencies; it is not a
blanket success cache. Listing by ID is live pagination, not a snapshot. No
command receipts exist for create/rename, so an interrupted response does not
authorize replay. Account selection remains device-owned; a sign-in establishes
principal/session evidence without choosing a workspace.

Used in [Account](../modules/internal/account/README.md). Related:
[wire admission](wire-admission-before-domain.md) and
[committed security refusals](committed-security-refusals.md). Verification:
`make backend-account-parity-test`. Next: how can durable storage preserve the
mapping, aggregate and integration envelope in one transaction?
