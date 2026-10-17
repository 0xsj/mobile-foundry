# Go source-copy starter

Status: implemented and independently accepted, CLI 0.4.0, 2026-10-13.

`--backend go` selects the explicit backend inventory. It includes runtime/Health,
Identity, Account, Audit, Notes, Files, Notifications and Realtime, their owned stores/migrations/tests and portable
process/database tooling. Supabase/Firebase, reserved modules and notebooks are
unselected. Native profile selection remains independent; Daily/Workspace/Browse
can accompany Go without implicit native auth/data wiring. Daily local-data
continues to use guest SQLite. Backend module subsets are not registered yet.

`--platform none --backend go` selects an API without native dependencies. It
refuses --profile, --with, --app-id and --ios-team. Native-only defaults remain
unchanged. `--go-module` requires Go and admits a conservative lowercase ASCII
domain/path up to 240 characters, with reserved/path/version-suffix refusals.
Default: example.com/NAME/backend. Generation requires Python only, no Go/network.

Registry and backend inventory status/identity must agree. Static checks validate
declared sources, overlays, Go module/toolchain, own imports, embedded SQL and
fixture presence; they do not compile arbitrary Go source or establish readiness.
Snapshot/exclusion/symlink/special-file policy is shared with native copies.
Required files cannot be excluded. Native/backend collisions with differing
content are refused. Failures remove only the newly owned destination and never
mutate source or existing output. Existing output/overlap refusals remain intact.

go.mod, own import paths and the architecture test prefix are rewritten. Ordinary
comment-free import groups are sorted by path after rewriting; unknown import
syntax is a generator drift failure with owned-output cleanup. This supports the
declared reference source and does not require Go/gofmt installed at initialization.
Runtime authority/capability/event/schema names are not branding replacements.
Pinned external dependencies/checksums remain unchanged. No replace/go.work link
back to the checkout is introduced.

Compose uses NAME-api and a project-owned retained named volume. DB/user are app;
the local example password is public development configuration. POSTGRES_PORT
defaults to 58432; the default Make URL follows it. Explicit DATABASE_URL/module
overrides retain their semantics. Generation creates no database/container and
copies no live .env/credentials. Migrations remain explicit and immutable.

Generated Make exposes go-dev/build/check/test/smoke, database config/up/down,
migrate/check and disposable integration commands alongside native commands.
CLI 0.14.0 additionally exposes operations acceptance and explicit production
config/build/database/migrate/up/down/maintenance/backup/restore commands. Public
production examples are selected explicitly; private SMTP/provider files are
excluded. See [the operational boundary](backend-operations.md) and
[current evidence](../../docs/blueprints/backend-connected-foundations.md).
The development runner explicitly selects AUTH_MODE=identity; binary config
still defaults to deny. Memory is default; persistent storage is PostgreSQL.
The generated guides describe ports, registration, scopes/versions/receipts,
environment ownership and deliberate production work. Co-location is stated
explicitly as unconnected native/backend composition unless the registered
[go-connected capability](go-connected-starter.md) selects native session/account/
Notes wiring.

Provenance adds backend/module/module-list inputs and backend inventory hash to
the selected raw source digest. API-only output has null native profile/app ID
and an empty platform list. A combined copy records nativeBackendConnection as
unconnected, or go-connected with its public apiBaseURL when explicitly selected.
No source checkout absolute path is embedded. Source copies do not
receive subsequent foundation changes automatically.

Independent evidence includes actual compiled memory API, auth/account isolation,
Notes CRUD/CAS/receipt replay, renewal/sign-out, PostgreSQL migration and killed
process recovery, and builds of both co-located native apps. Current Go backend
readiness is standalone; the connected Daily profile has its own separate native
acceptance in [connected construction](../../docs/blueprints/go-connected-starter.md).
See [setup/evidence](../../docs/blueprints/go-starter.md) and
[CLI behavior](project-cli.md).
