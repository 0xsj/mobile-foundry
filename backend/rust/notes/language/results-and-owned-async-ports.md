# Results and owned async ports

Claim: Rust can express anticipated failure and swappable async dependencies
without importing the transport or detaching their lifetimes.

Origin/evidence: 2026-10-18 implementation and Rust 1.86 execution of the
workspace's failure, readiness and drain tests. The
[Rust Book on Result](https://doc.rust-lang.org/book/ch09-02-recoverable-errors-with-result.html)
explains recoverable error propagation; the standard library documents
[Pin](https://doc.rust-lang.org/std/pin/index.html),
[Arc](https://doc.rust-lang.org/std/sync/struct.Arc.html) and
[Send/Sync](https://doc.rust-lang.org/book/ch16-04-extensible-concurrency-sync-and-send.html).

`Result<T, Failure>` is the standard success/error enum. `?` returns an error to
the caller rather than panicking. `map_err` deliberately translates a dependency
failure into the consumer's condition; `with_cause` retains the original source.
`Option<T>` means presence/absence, so a missing dependency is never represented
as successful absence by swallowing an error.

```rust
// Excerpt: Health owns the port; implementations own their concrete future.
pub type ProbeFuture<'a> = Pin<Box<dyn Future<Output = Result<(), Failure>> + Send + 'a>>;
pub trait Readiness: Send + Sync {
    fn check(&self) -> ProbeFuture<'_>;
}
```

The exact [source](../../internal/health/src/app/query.rs) supplies imports.
`dyn` selects an implementation at runtime. `Box` owns its allocation; `Pin`
keeps a polled future's location stable. `'_` ties the returned future to the
borrow of `self`; the future cannot outlive the dependency. `Send` allows the
future to move between runtime worker threads; `Sync` allows shared references
to the port. Neither marker supplies a lock or makes arbitrary mutation safe.
`Arc<dyn Readiness>` supplies shared ownership, not permission to mutate state.

Native async trait methods are an alternative for statically chosen generic
implementations; directly using them through this trait object is not the same
object-safe API. A boxed future makes the runtime-swappable seam explicit here.
This introduces allocation/dynamic dispatch per probe; no performance benefit
is claimed. Business domains should use plain values rather than async traits
unless an external dependency actually requires them.

`Failure` retains its cause in an owned `Arc<dyn Error + Send + Sync>`, allowing
clones without borrowing a temporary stack error. Its default Debug/Display are
redacted and HTTP uses an explicit public projection. `Error::source` remains
private diagnostic access; printing an entire source chain can still expose it.
Unlike Go's joins, this first type has one source and no multi-error algebra.

Used in [Kernel](../modules/pkg/kernel/README.md) and
[Health](../modules/internal/health/README.md). Related:
[owned drain](../patterns/readiness-and-owned-drain.md). Next question: when
would a statically generic adapter be simpler than a trait object?
