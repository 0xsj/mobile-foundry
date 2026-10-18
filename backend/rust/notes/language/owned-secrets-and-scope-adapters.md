# Owned secrets and scope adapters

Claim: explicit owned values and consumer-owned traits let application work keep
its lifetime and failure meaning without importing the HTTP runtime.

Origin/evidence: 2026-10-18 Identity implementation and cancellation/atomicity
execution. Read the standard library's
[Drop explanation](https://doc.rust-lang.org/book/ch15-03-drop.html),
[trait objects](https://doc.rust-lang.org/book/ch18-02-trait-objects.html) and the
existing [Result/async-port note](results-and-owned-async-ports.md).

Secret wraps Zeroizing<String> and provides a redacted Debug implementation.
Moving a Password into a blocking closure transfers ownership; the closure no
longer borrows a temporary request DTO. Clone explicitly creates another owned
buffer, each with its own drop. Wire/crypto adapters expose bytes deliberately;
there is no derived serializer or display method that prints a Secret by default.
Principal/session formatting is also redacted. This reduces accidental exposure
without claiming that all copies of a credential are erased from memory.

Scope is a Send + Sync trait containing check and safe provenance,
not an HTTP request. The transport owns HttpScope, which adapts RequestContext's
deadline and metadata. Memory checks Scope again after taking the lock and before
commit; the password adapter checks before/after bounded computation. An aborted
async future drops local work but does not roll back a committed store change.

The Account follow-up moves this mechanical Scope/Metadata interface into
[Kernel context](../../pkg/kernel/src/context.rs), re-exported by Identity's
ports. Capability invocation wraps the same scope with its own monotonic budget.
Principal/session authority remains consumer-owned evidence rather than a field
on shared context. Account's wire request borrows its credential while encoding
into a zeroizing buffer, avoiding a separate JSON Value copy for that encoding.

Ports return a pinned boxed future borrowed from the selected adapter and Scope.
The application sees only that interface, so memory/security adapters can be
substituted. The test-only password adapter exercises policy/race behavior;
separate tests execute actual cryptography. Module composition wires the exact
typed dependencies; there is no dynamic service lookup from business code.

Gotchas: a borrowed future cannot enter spawn_blocking directly because the job
requires owned 'static data. It must take the minimal owned inputs rather than
retaining a whole request. A future SQL adapter must preserve cancellation and
uncertain commit semantics rather than mapping them to successful absence.
Used in [Identity](../modules/internal/identity/README.md). Related:
[newtype invariants](newtypes-and-transport-errors.md) and
[blocking KDF ownership](../substrate/argon2-and-blocking-lifetime.md). Next:
how should a future remote adapter retain the same private credential lifetime?
