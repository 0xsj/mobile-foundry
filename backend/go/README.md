# Go reference backend

This is the owned API profile, following Bento's modular Go architecture.
Directories are reserved; there is no Go module or executable yet.

## Initialize the module

From this folder, choose the import path you want to keep:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/backend/go
go mod init mobilefoundry/backend
```

`mobilefoundry/backend` is a local module path. Use your eventual repository
URL instead if you want a publishable import path. Run `go mod tidy` after
adding the first source and dependency. The command is `go mod init`; Git is
already initialized at the Mobile Foundry repository root.

## Reserved layout

```text
cmd/server/                  # Process startup and concrete composition
pkg/
  errors/                    # Failure vocabulary and public projection
  clock/                     # Time interfaces and implementations
  id/                        # Identifiers
  env/                       # Configuration parsing
  logger/                    # Logging boundary
  httpx/                     # HTTP support
  principal/                 # Request identity and authority values
  events/                    # Envelopes and delivery mechanics
  persistence/postgres/      # Shared PostgreSQL infrastructure
internal/
  architecture/              # Boundary checks when implemented
  identity/
  account/
  audit/
migrations/                  # Schema changes owned by each module
scripts/                     # Backend commands when needed
tests/integration/           # Cross-module verification
notes/                       # Learning walkthroughs and findings
```

Identity, Account, and Audit each reserve `domain`, `app/command`, `app/query`,
`infra/postgres`, `infra/events`, `infra/capabilities`, `transport/http/v1`, and
`transport/capability/v1`. Identity also reserves security and mail adapters.
The folders contain `.gitkeep` files, not pretend implementations.

Module composition will live in `internal/<module>/module.go` and `build.go`.
Keep pure domain rules inward, application ports explicit, and adapters chosen
at composition. Go uses ordinary error returns; public projection must keep
private diagnostics out of wire responses. See [backend rules](../AGENTS.md)
and [Bento conventions](../../../bento/backend/CONVENTIONS.md).

Start one process with the modules needed by the first connected workflow.
Add database services and other Go infrastructure when that implementation
begins; the provider stacks are independent development profiles.

[Learning notes](notes/README.md) will explain each implemented slice.
