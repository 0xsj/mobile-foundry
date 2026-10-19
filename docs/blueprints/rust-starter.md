# Rust starter: CLI 0.19.0

Rust is ready to start standalone APIs with the tested local baseline. Source
and generated output have separate acceptance. Go remains another independent
backend selection; [native connected Rust](rust-connected-starter.md) has its own
explicit capability and acceptance.

From any terminal parent directory after installing the CLI:

```sh
mobile-foundry doctor --platform none --backend rust
mobile-foundry init canopy --platform none --backend rust
cd canopy
make rust-check rust-test rust-smoke
make rust-dev
```

Python 3.11+ runs initialization. Building/checking needs Rust/Cargo 1.86+, rustfmt,
Clippy and OpenSSL for local TLS tests. Dependencies download only when the user
runs build/test commands. The generated dev runner selects owned Identity with
memory storage. Its SETUP.md provides explicit registration/sign-in, PostgreSQL,
private provider configuration and portable operations commands.

The default API package is NAME-api; --rust-package can choose another bounded
lowercase/hyphenated name without workspace/dependency collisions. The api-server
binary and reusable package names stay stable. Cargo.toml and exactly one local
Cargo.lock package are changed together. Dependency versions/checksums and wire
authority/capability/schema names are retained. Compose defaults and default image
identity are isolated by product name. --go-module and --rust-package are separate
backend-specific inputs.

Co-location, with existing UI-only native behavior:

```sh
mobile-foundry init canopy --platform both --backend rust \
  --app-id com.example.canopy --rust-package canopy-api
```

Daily/Workspace/Browse and Daily local-data keep their own selection rules. No
Rust native session/data connection is inferred. Choose --with rust-connected
for its explicit Daily composition; go-connected still requires Go.

The inventory selects 335 files, including 24 crates and 30 shared fixture files.
The CLI reads effective Cargo manifests, verifies local dependency/include/module
paths and required resources, then copies regular files under the common owned
destination policy. Cargo is not executed by init. Caches, .data, .cargo credentials,
private TOML/JSON/provider keys, notebooks and Go implementations are omitted.
The copied Makefile has no reference to repository parity scripts. Both smoke
and PostgreSQL drivers import helpers solely from the copied Rust directory.

Validation output, observed 2026-10-18:
`/private/tmp/mobile-rust-starter-5pcdgymo/verified-api`, API package `canopy-api`,
generator 0.19.0. It was generated while the registry marked Rust candidate;
standalone registration was promoted only after the following independent checks.

- Generated root rust-check/rust-test/rust-smoke: format, Clippy, locked builds,
  copied fixture/behavioral tests, scoped API receipts/CAS/bytes/inbox and lifecycle.
- Generated root rust-integration-test: fresh PostgreSQL 18.6 Docker container,
  unmigrated refusal, copied transaction/job rollback and SIGKILL checks, API
  restart with retained Notes/Files/pending notification and persisted sign-out.
- Repository-owned cli/acceptance/rust/verify.py against that output: no checkout
  paths in selected build inputs, configured Cargo identity, actual rust-dev Make
  runner with Identity default, scoped workflows and owned process cleanup.
- Generated root rust-operations-test: its own release image, non-root/read-only
  health, actual local Caddy/native socket, Mailpit verification/reset, private
  metrics, paired backup/fresh-project restore, retained receipts/content/contact/
  challenge, damaged/existing-target refusal, maintenance and private logs.

The Docker acceptance removes only its owned projects/volumes and auto-built
image. It leaves unrelated resources and retained development databases alone.
The primary output remains available for inspection; its own build artifacts are
under backend/rust/target. It does not require this checkout to continue working.

Read [behavior](../../contracts/behavior/rust-starter.md),
[the parity inventory](../../backend/rust/PARITY.md) and
[Cargo closure reasoning](../../notes/techniques/cargo-closure-and-independent-starters.md).
The static checker and CLI copy tests cannot prove execution; the independent
checks above establish the registered standalone profile. Public DNS/TLS, live
mail/push, full Go parser/network equivalence,
automatic sync and product retention/erasure remain deliberate extensions.
