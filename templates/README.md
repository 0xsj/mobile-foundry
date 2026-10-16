# Project extraction profiles

[Go backend inventory](backends/go.json) selects implemented runtime/Health,
Identity, Account, Audit and Notes sources, tests, migrations, scripts and the
Account fixture. [Go overlays](overlays/go) supply portable Identity-enabled Make
defaults and an environment example. The CLI rewrites the Go module/import-policy
prefix and ordinary import ordering, isolates Compose by product name, and writes
independent setup/build commands. No reserved modules or repository-only notebooks
are copied. It supports API-only or co-located native output; connected native
composition remains a separate capability. See [construction](../docs/blueprints/go-starter.md).

[native-daily.json](profiles/native-daily.json) inventories the first local UI
profile: clean Swift `apps/ProductApp` and Kotlin `project/product` entries,
four Daily tabs, Solid/Glass and the UI → Query → Kernel dependency closure.
It does not select Notes, Session, graphics, camera, backends or catalog screens.

The inventory records exact source trees/files, identity edit locations, shared
fixtures and exclusions. Paths retain repository depth so local package and test
fixture references can be copied without referring back to this checkout. It
selects sources/tests/manifests explicitly, rather than copying package readmes
that depend on repository-only learning notes.

The [Kotlin settings overlay](overlays/kotlin/settings.gradle.kts) registers only
the four selected modules. The current repository settings still register both
catalog and product. Swift generates a separate product Xcode project from its
selected `project.yml`; generated project files are not part of this inventory.

Run `make starter-check` to validate file existence, dependency closure, selected
fixture directories, overlay registration and product/catalog source separation.
The checker uses the same validation as the [local CLI](../cli/README.md), while
remaining read-only. The CLI consumes registrations from
[mobile-foundry.starters.json](../mobile-foundry.starters.json), applies the overlay,
excludes machine/private artifacts and edits product identity in independent output.
Canvas and Guided setup remain catalog candidates for later profiles.

## Workspace and Browse

[native-workspace.json](profiles/native-workspace.json) and
[native-browse.json](profiles/native-browse.json) select app-owned list/detail or
search/detail navigation. [Shared shell overlays](overlays/shells) supply runtime
branding, themed headers and isolated build/metadata checks. The
[Workspace](overlays/workspace) and [Browse](overlays/browse) overlays supply
typed placeholder identities, app state, screens and native interaction checks.
Neither profile copies the Daily screens or catalog fixtures/actions.

Each inventory selects the same UI/Query/Kernel closure and product entry but a
different complete app source set. Required state/screens/tests must survive
selection and exclusions. Native targets/module names remain stable; installed
identity is configured independently. The CLI retains Daily as its default and
registers local-data only for Daily. Read [behavior](../contracts/behavior/shell-starters.md)
and [construction](../docs/blueprints/shell-starters.md) for scope and acceptance.

## Optional local data

[native-daily-local-data.json](profiles/native-daily-local-data.json) is the
complete registered inventory for `native-daily --with local-data`. It selects
local Services/Data sources, SQLite CRUD and observation checks, and the shared
local Notes fixtures. [Local-data overlays](overlays/local-data) supply portable
local module builds and an app-owned Library feature with composition, state,
forms, delete confirmation and an independent live reader.

The full reference Data modules also own account adapters, and full Services
modules own HTTP adapters. The local manifests deliberately omit those sources
and dependencies. The HTTP Notes implementation has a separate native source
file; domain ports remain transport-independent. Partial source selection is
explicit and required files are checked. Ports/SQLite/observation implementation
is copied from its existing native owner, not reimplemented in a template.
Product state/presentation overlays are editable app examples, with their own
included tests. They are intentionally separate from catalog-specific routing.

The registry lists capabilities and accepted combinations rather than merging
overlays implicitly. Validation uses the effective build graph after overlays.
`make starter-check` checks all registered profiles/combinations by default;
`--profile PATH` still checks a single inventory. Unregistered combinations are
refused. Read [behavior](../contracts/behavior/local-data-starter.md) and
[construction](../docs/blueprints/local-data-starter.md).

Reusable source stays in the native modules; templates own extraction metadata
and configuration overlays. Follow [starter readiness](../docs/STARTER-READINESS.md)
for independent generated-product acceptance before announcing readiness.
