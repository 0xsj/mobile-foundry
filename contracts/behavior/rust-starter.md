# Rust backend starter extraction

Established 2026-10-18 through CLI 0.19.0. Rust is a registered standalone backend
selection under the [project CLI contract](project-cli.md). It can be generated
alone or beside an independently selected native shell. Native Rust connection
is not selected by co-location; [rust-connected](rust-connected-starter.md) is an
explicit capability with separate acceptance, and go-connected still requires Go.

`--backend rust` selects the explicit inventory: 24 local crates, runtime/Health,
Identity/recovery, Account, Audit, Notes, Files, Notifications/installations,
optional providers/jobs, Realtime and portable operations. It includes locked
dependencies, migrations, owned tests/drivers and 30 shared fixtures. Preserve
relative directory depth for literal Rust include/module paths. No Go source,
repository-only notebooks, build output, application state, Cargo credentials
or private provider configuration is copied.

`--rust-package` changes only the API package, default NAME-api. Admit 1..64
lowercase letters/digits with single internal hyphens, starting with a letter.
Refuse device-name aliases, local workspace and locked dependency collisions;
Cargo package selection must remain unambiguous. --go-module requires Go;
--rust-package requires Rust. The explicit api-server binary and reusable crate
names remain stable. Rewrite only the owned package entry in Cargo.toml and
Cargo.lock; retain all dependency versions/checksums. Product names also isolate
local/production Compose defaults and the default image identity.

Initialization needs Python 3.11+, with no Cargo invocation, dependency download,
Git initialization or secrets configuration. Effective inventory validation
checks selected workspace members/MSRV, local path dependencies, literal includes,
module path attributes, required migrations/fixtures and overlay destinations.
Computed includes and registry patch/replace overrides need explicit support.
This validates the declared source grammar, not every Cargo/Rust program.
Existing output, symlinks and private/excluded data obey the common owned-copy
policy. Failure/cancellation removes only newly owned output and preserves source.

Generated root commands delegate to backend/rust. rust-dev explicitly defaults
Identity; the bare binary still denies authentication. Memory needs no service.
Local PostgreSQL uses a product-owned Compose project, optional POSTGRES_PORT and
explicit migration. The integration driver owns a fresh cluster/container and
never tests against a supplied application DSN. It preserves the copied store/job
rollback/SIGKILL checks plus API receipt/bytes/inbox/session restoration. Portable
Docker acceptance also lives entirely in the output and owns its disposable
image/projects. Private keys, mail and cloud sends remain off unless configured.

doctor reports Rust/Cargo/rustfmt/Clippy/OpenSSL without building/installing.
Rustup auto-install is disabled for its probes. Tool observations do not prove
application build, Docker availability, public TLS or live provider delivery.
Provenance records rustPackage, selected modules, hashes and generator version;
it contains no source absolute path. Generated files do not depend on the CLI.

Acceptance: an independent API with a renamed package/lock passes format, Clippy,
workspace build/tests, memory smoke, the real generated Make runner, disposable
PostgreSQL rollback/process-loss/retained-state checks and its freshly built
Docker/Caddy/SMTP/metrics/backup-restore flow. CLI tests cover Rust-only and all
three native co-locations, identity admission, missing closure, state/credential
exclusions, source preservation and cleanup. Native UI-only source is unchanged;
these copy checks do not establish connected Rust native products.

Read [construction and evidence](../../docs/blueprints/rust-starter.md).
Standalone readiness covers this local API baseline. Native connection follows
its separate selected acceptance above. The existing Rust routing/network
differences, public hosting/TLS, external delivery and product-specific
retention/sync policies remain deliberate extensions.
