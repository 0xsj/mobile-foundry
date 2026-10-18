# Consuming builders and immutable registries

Claim: moving a builder into its serving representation makes a registration
phase boundary explicit without a mutable frozen flag in every runtime call.

Origin/evidence: 2026-10-18
[capability Builder/Registry](../../pkg/capability/src/lib.rs), Rust 1.86 compilation,
caller/duplicate/deadline tests and real Account composition. Primary language
reference: [ownership](https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html).

Builder owns a mutable map. freeze takes self by value, moves that map into a
Registry and returns Arc<Registry>. Rust moves invalidate further use of the
old builder. Runtime clients clone the Arc to share immutable registrations;
they do not clone every provider or hold a registration mutex during a call.

```rust
// Excerpt: the method consumes assembly ownership, rather than toggling shared state.
pub fn freeze(self) -> Arc<Registry> {
    Arc::new(Registry { providers: self.providers })
}
```

This is a small phase-specific API, not a universal typestate framework. An
alternative is Go's lock plus frozen boolean, which permits one registry type
through assembly and serving. Here separate types let the compiler rule out
post-freeze registration. The [process](../../cmd/server/src/main.rs) is the
actual owner that registers, freezes, verifies requirements and selects a client.

Gotchas: Arc does not make mutable provider internals safe by itself; Handler and
Invoker still require Send + Sync. Immutability also does not establish caller
permission or provider availability, which remain explicit runtime checks.
The object-safe boxed async ports preserve borrowed request/scope lifetimes;
the borrowed invocation future remains owned by its caller and can be dropped.

Related: [async trait ports](results-and-owned-async-ports.md),
[capability walkthrough](../modules/pkg/capability/README.md). Verification:
`make rust-check rust-test`. Next: when would runtime provider replacement require
a different lifecycle API rather than adding mutation to this serving registry?
