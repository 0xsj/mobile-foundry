# Cargo and the HTTP runtime

Claim: the workspace lock and explicit crate/runtime ownership make this Rust
slice reproducible without implying the defaults of two HTTP servers are equal.

Origin/evidence: observed 2026-10-18 on macOS with rustc/Cargo 1.86.0. Locked
dependencies include Axum 0.8.9, Hyper 1.11.1, hyper-util 0.1.20, Tokio 1.53.2,
serde_json 1.0.154 and uuid 1.26.1. The admission follow-up adds serde_ignored
0.1.14, mime 0.3.17 and direct fallible getrandom 0.4.3 use with its std error
feature. The initial runtime dependencies resolved from the local cache. PostgreSQL adds
SQLx 0.8.6 with only PostgreSQL/Tokio-rustls/chrono/JSON features; the locked
driver dependencies were fetched and compiled with Rust 1.86. Newer MSRV-only
releases remain outside the selected lock.
[Cargo.lock](../../Cargo.lock) is included; generated target files
are ignored. Fresh machines still need registry access or a populated cache.

The [virtual workspace](../../Cargo.toml) explicitly sets resolver 3, edition
2024 and minimum Rust 1.86. Workspace package metadata, dependency versions and
unsafe-code policy are inherited by the eighteen member crates. Resolver 3 considers
Rust-version compatibility: the cached uuid 1.28.0 requires a newer compiler, so
Cargo selected compatible 1.26.1. Changing a lock needs its own check rather than
assuming every newest dependency works with the supported compiler.

Primary references: [Cargo workspaces](https://doc.rust-lang.org/cargo/reference/workspaces.html),
[resolver 3](https://doc.rust-lang.org/stable/edition-guide/rust-2024/cargo-resolver.html),
[Axum serve](https://docs.rs/axum/0.8.9/axum/fn.serve.html),
[Hyper HTTP/1 builder](https://docs.rs/hyper/1.11.1/hyper/server/conn/http1/struct.Builder.html)
and [Tokio JoinSet](https://docs.rs/tokio/1.53.2/tokio/task/struct.JoinSet.html).

Axum supplies request/response types, middleware and routing. This server uses
Hyper's HTTP/1 connection builder directly so the process owns every connection
task, header timeout and forced join. Tokio supplies the listener, scheduling,
signals and timers. No request handler is detached by the runtime. The Health
application port exposes a boxed future without importing these HTTP types.

Gotchas: Hyper's 16 KiB buffer setting is not Go's header byte accounting, and
its five-second header timeout is not the entire Go read/write/idle policy.
An async timer cannot interrupt synchronous code that never yields. Panic
catching handles unwind-mode response construction, while the process also needs
its redacted global hook; catch_unwind alone does not silence the default hook.

Observed checks: format/Clippy/build, Rust workspace behavioral tests and the shared
runtime/Identity/Account/Audit corpora pass. Capability/delivery tests additionally use
Tokio's test-util paused clock for an owned two-second timeout, without wall-clock
sleeping or changing production time. These establish the recorded cases on this
toolchain, not performance, Linux execution or a production deployment. Used in
[the server walkthrough](../modules/internal/server/README.md). Related:
[Result/async ports](../language/results-and-owned-async-ports.md). Next: which
remaining network budgets should be implemented before exposing business writes?
