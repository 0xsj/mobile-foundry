# Rust-connected Daily starter

Established 2026-10-19. CLI 0.20.0 adds the explicit Daily selection
`--with rust-connected --backend rust` for Swift, Kotlin or both. It selects the
same native ports, composition, models, forms and private storage as
[Go-connected Daily](go-connected-starter.md), with the independently copied
[Rust backend](rust-starter.md) as its actual API. Backend choice does not fork
native session, receipt, cache, workspace or live-read policy.

`--backend rust` alone remains co-location. Rust-connected requires Rust;
Go-connected requires Go. Other shells, local-data plus a connected capability,
both connected capabilities and backend-only connected selection are refused
before output creation. A capability inventory explicitly selects all native
dependencies, tests, fixtures and connection configuration.

The existing public endpoint admission, Debug loopback/Release HTTPS rules and
deployment-bound credential vaults apply unchanged. Metadata records
nativeBackendConnection=rust-connected and rustPackage; it never claims a Go
module. Cargo package/lock identity and reusable crate/binary naming follow the
standalone Rust contract. Generated documentation projects Rust build/run and
operational commands and includes a self-contained connected acceptance driver.

GoSessionAuthority is the existing HTTP wire adapter's source name. Rust
implements the admitted wire contract; this selection does not add a second
native auth implementation or weaken admission to accommodate a provider.
Library keeps exact session leases, a scoped SQLite pending-command journal,
receipt-first explicit recovery, read-only first-page caching and lossy live
refetch hints. Account keeps explicit registration/sign-in, owner workspace
create/CAS rename, secure restore/renew/sign-out and verification/reset/change.
There is no automatic registration, Account mutation or pending Notes replay.

The copied driver reads the selected backend from product metadata, verifies a
Rust package match, builds only that product's locked Rust API with an output-owned
target directory, and starts an owned memory process. No Go toolchain or Go source
is needed for Rust acceptance. Existing test fixture/environment names retain
their Go wire-contract names. Host suites use real URLSession/OkHttp and native
sockets; device suites must additionally exercise actual generated screens and
secure-vault/journal restoration across process termination, with no skipped
selected tests. Temporary mail capture, fault proxy and ADB mappings remain owned.

Readiness and observed execution are recorded in
[construction/evidence](../../docs/blueprints/rust-connected-starter.md).
Both generated native products pass their actual screen/process acceptance:
36 iOS tests, ten separate Android screen stages and seven native Data tests,
with zero failures/skips. The capability is registered after that execution.
Host transport checks alone do not establish connected starter readiness.
Memory loses server state on exit; the Rust PostgreSQL and Docker baselines have
their own standalone acceptance. This slice does not establish connected native
PostgreSQL restart behavior, public hosting, device performance, live push,
additional native feature selections, full Go wire equivalence or automatic sync.
