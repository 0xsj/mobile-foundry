# Local project CLI

CLI 0.26.0 adds read-only file destinations to Files + Inbox: Open file resolves current authorized metadata, with explicit verified preview and unavailable-target handling. Opening leaves read state unchanged. Read [behavior](../contracts/behavior/notice-destinations.md) and [execution evidence](../docs/blueprints/connected-media.md).

CLI 0.25.0 adds durable manual Files recovery to Files-only and Files + Inbox: one private journal stages original metadata/bytes before HTTP and restores them across process exit. Restore never resends. Read [behavior and evidence](../docs/blueprints/connected-files.md).

CLI 0.24.0 adds explicit Files recovery review to Files-only and Files + Inbox selections. Keep preserves the original command; Forget local recovery releases local bytes/key and requires a fresh read without claiming rollback or cleanup. Read [behavior, evidence and limits](../docs/blueprints/connected-files.md).

CLI 0.23.0 adds an explicit [Files + Inbox selection](../docs/blueprints/connected-media.md):

```sh
mobile-foundry init canopy --platform both --with files --with inbox --with go-connected --backend go
```

Use rust-connected and --backend rust for Rust. Today uploads through Files;
Activity reads backend-created notices and refreshes on entry. Each feature keeps
its own recovery rules; Files now uses durable manual storage. Both native/backend combinations
are ready for the tested local baseline; read the guide for evidence and limits.

CLI 0.22.0 adds an optional [account Files](../docs/blueprints/connected-files.md)
selection for either matching connected Daily backend:

```sh
mobile-foundry init media --platform both --with files --with go-connected --backend go
```

Use rust-connected and --backend rust for Rust. Today selects gallery imports,
explicit uploads, verified download previews and confirmed deletion. Pending
Files recovery is now bounded and durable in CLI 0.25.0; restore never resends intent.
Files and Inbox can be selected together in CLI 0.23.0; see the combined guide
below for current independent verification and limits.

CLI 0.21.0 adds an optional [account inbox](../docs/blueprints/connected-inbox.md)
to Daily with either connected backend:

```sh
mobile-foundry init canopy --platform both --with go-connected --with inbox --backend go
```

For Rust, use --with rust-connected --with inbox --backend rust. Activity selects
the scoped inbox; omitting inbox preserves the placeholder. Read SETUP.md for
unread/filter/paging, media settings, uncertainty/readback and owned acceptance.

CLI 0.20.0 adds explicit [Rust-connected Daily](../docs/blueprints/rust-connected-starter.md):
the same native session/accounts/Notes composition, with a copied Rust API.

```sh
mobile-foundry init canopy --platform both --with rust-connected --backend rust \
  --app-id com.example.canopy --rust-package canopy-api
cd canopy
make rust-dev
```

Choose swift or kotlin for one native app. Generated SETUP.md includes connected
acceptance, Rust persistence/operations and device networking. Backend selection
alone remains co-location. Go-connected still requires Go; Rust-connected requires
Rust. Other shells and local-data combinations are not inferred.

CLI 0.19.0 registers the [standalone Rust backend](../docs/blueprints/rust-starter.md).
It copies 24 local crates, locked dependencies, migrations, portable drivers and
30 shared fixtures, with independent memory/PostgreSQL/Docker acceptance.

```sh
mobile-foundry doctor --platform none --backend rust
mobile-foundry init canopy --platform none --backend rust
cd canopy
make rust-check rust-test rust-smoke
make rust-dev
```

`--rust-package` optionally changes the API package (default NAME-api); use 1–64
lowercase letters/digits with single internal hyphens, starting with a letter.
Workspace/dependency collisions are refused. The api-server binary and reusable
crate names stay stable. `--go-module` belongs to Go; `--rust-package` belongs to
Rust. Native commands require macOS/Linux, Rust/Cargo 1.86+, rustfmt/Clippy and
OpenSSL for TLS tests. Initialization still needs only Python 3.11+; doctor disables
Rustup auto-install. Docker and optional local PostgreSQL are separate prerequisites.

Rust can also be co-located with native shells via `--backend rust`; native
connection requires the explicit rust-connected capability above.
The generated root includes rust-integration-test, rust-operations-test and explicit
database/production wrappers. Each output owns its package/lock/Compose defaults;
private Cargo state, keys and retained app data are excluded.

CLI 0.18.0 selects workspace create/rename forms behind AccountManagement, strong
version checks and explicit read/review after uncertainty. See [setup and evidence](../docs/blueprints/native-workspaces.md).

CLI 0.17.0 adds native Create account forms through IdentityRegistration in
Daily + go-connected. Success leads to explicit sign-in; passwords remain transient
and lost acknowledgements never trigger automatic registration replay. Read
[registration setup and evidence](../docs/blueprints/native-registration.md).
Existing [read caching](../docs/blueprints/notes-read-cache.md) and
[security/live reads](../docs/blueprints/native-identity-live.md) remain selected.

CLI 0.14.0 adds [backend live hints, core identity flows and portable Docker
operations](../docs/blueprints/backend-connected-foundations.md) to every Go
selection. The generated Makefile includes operations acceptance and explicit
production build/migrate/backup/restore targets. Private mail/provider settings
are excluded; no cloud account or public deployment is created.

The CLI includes `--with go-connected --backend go` for Daily for Swift, Kotlin or
both. Account provides real Go sign-in/restore/renewal, workspace selection and
sign-out; Library provides remote Notes CRUD, paging and explicit receipt recovery.
See [connected setup and acceptance](../docs/blueprints/go-connected-starter.md).

```sh
mobile-foundry init canopy --platform both --with go-connected --backend go \
  --app-id com.example.canopy --go-module github.com/your-org/canopy/backend
cd canopy
make go-dev
```

Register a development user using SETUP.md, then sign in from Account. Optional
`--api-base-url` defaults to `http://127.0.0.1:8080/v1` for Debug; Release requires
HTTPS. Android emulator/USB needs an owned ADB reverse mapping. The selected apps
use native secure credential storage and the existing ports. Pending writes stay
in a private SQLite journal before HTTP, including manual recovery after process
restart. Unsaved drafts stay transient; multi-item sync and native Files/inbox/push
are separate selections. Read [journal behavior](../contracts/behavior/pending-notes.md).
Local-data + go-connected and connected Workspace/Browse are not registered.

CLI 0.11.0 includes [owned push-job recovery](../docs/blueprints/push-job-recovery.md),
its 0004 migration, atomic adapters and old-row upgrade checks. Generated setup
explains cancel-before-sending, one linked replacement per source and explicit
duplicate-risk acknowledgement. No keys, worker or connected native recovery UI
are enabled by copying the source.

CLI 0.10.0 includes [queued Go previews](../docs/blueprints/push-jobs.md): the owned
0003 migration, state/ports/adapters, fenced worker and guarded enqueue/status API.
The generated guide explains explicit PostgreSQL/provider/queue selection and
uncertain outcomes. No provider credentials, active worker or native connected
profile is selected by copying the source.

`mobile-foundry` copies a selected native starter into `PARENT/NAME`. The output
contains its own source, native build configuration, tests, fixtures, Gradle
wrapper and setup guide. It builds without this checkout or the CLI.

Python 3.11+ is the only generator dependency. Native tools are needed later to
build the selected apps. No package installation is required for the CLI.

## Install and use

From the repository root:

```sh
make cli-install
export PATH="$HOME/.local/bin:$PATH"
mobile-foundry list
```

Installation links `~/.local/bin/mobile-foundry` to this checkout's executable.
It is idempotent and refuses to overwrite another command. Keep the checkout
while using that link; generated products are independent copies. You can also
run `/path/to/mobile-foundry/bin/mobile-foundry` directly without installing.
Use `make cli-install CLI_PREFIX=/your/prefix` for a different installation root.

In an existing directory where you want to create products:

```sh
mobile-foundry doctor --platform both
mobile-foundry init canopy --platform both \
  --with local-data --display-name Canopy --app-id com.example.canopy
cd canopy
make help
```

`init` writes `./canopy`; it refuses any existing destination, including an empty
directory. `--output /existing/parent` selects a different parent. A terminal can
prompt for the platform; scripts must pass `--platform`. Daily is the default
profile. Select Workspace or Browse explicitly with `--profile`.

Supported combinations:

| Platform | Output |
| --- | --- |
| `swift` | Selected iOS shell plus UI, Query and Kernel packages |
| `kotlin` | Selected Android shell plus UI, Query and Kernel modules |
| `both` | Both native products with the same installed identity/display inputs |

The Daily shell has Today, Library, Activity and Account, outline tabs and
Solid/Glass selection. Without `--with`, its screens are placeholders. Add
`--with local-data` for a working Library Notes example, local Services/Data,
SQLite persistence, CRUD state and live observation. All three platforms above
support that combination. Backend `none` is the default;
Go is available through `--backend go`, selecting Health, Identity, Account,
Audit, Notes, Files and Notifications with memory/PostgreSQL stores. Notifications
adds the upload-ready inbox, read state and media preferences; the native inbox
is an explicit optional selection, and cloud push remains separate. Files uses memory
bytes or a private local directory; cloud storage adapters remain separate. Generate an API on its own:

```sh
mobile-foundry doctor --platform none --backend go
mobile-foundry init canopy-api --platform none --backend go \
  --go-module github.com/your-org/canopy/backend
cd canopy-api
make go-check go-test go-smoke
make go-dev
```

`--platform none` requires a backend and refuses native profile/capability,
app-ID or signing inputs. `--go-module` accepts a bounded lowercase domain/path;
the default is `example.com/NAME/backend`. Imports and the architecture test's
prefix are rewritten and import groups sorted without requiring Go during
initialization. Go 1.27.1+ builds the API. The development runner explicitly selects
Identity; the bare binary retains deny as its default.

Add `--backend go` to a native command to include an independent API alongside
Daily/Workspace/Browse, including Daily local-data. Native screens retain their
chosen local behavior. Add `--with go-connected` on Daily to select the connected
composition above; backend co-location alone does not connect a shell.

Each output has its own Compose name/volume. Set POSTGRES_PORT when starting
another local database; Make's default DATABASE_URL follows it. The generated
SETUP.md explains registration, explicit migrations and isolated PostgreSQL tests.

Additional UI-only shell profiles:

```sh
mobile-foundry init workbench --profile native-workspace --platform both \
  --app-id com.example.workbench
mobile-foundry init collections --profile native-browse --platform swift \
  --app-id com.example.collections
```

Workspace has compact list/detail navigation and expanded panes at 700 available
points/dp, with a native large-text fallback. Browse has local collection search,
empty results, clear and detail/back navigation that retains the search. Both
have a Glass switch and app-owned placeholder identities/screens. Android saves
navigation/search/theme through Activity recreation; fresh launches start at
defaults. These profiles do not include durable data or saved collection actions.
Read [shell starter behavior](../contracts/behavior/shell-starters.md).
`local-data` is currently compatible only with Daily; other selections are refused.

Only registered combinations are admitted. `--with` is repeatable for future
capabilities, but repeated IDs, unknown IDs and unsupported combinations fail
before copying. Session/HTTP selection is included in go-connected. Graphics and
Canvas/Setup shells remain future extractions. The local-data combination includes
no networking or authentication.

Names use 1–80 lowercase letters/digits/hyphens, starting with a letter and ending
with a letter/digit. Default display name is the title-cased name; default app ID
is `dev.example.` followed by the name without hyphens. Supply your own identity
for actual products. App IDs use lowercase dot-separated segments starting with
letters and containing letters/digits, with no underscores or hyphens, so the
same ID is valid for both native platforms. `--ios-team ABC1234567` optionally
sets an Apple team for Swift output.
Display names are single-line text, up to 60 characters, without xcconfig
comment/substitution syntax. These limits keep configuration edits predictable.

## Work in the generated project

Read its `SETUP.md`. From the generated root, use the selected commands:

```sh
make ios-build
make swift-test
make ios-test IOS_TEST_DESTINATION='platform=iOS Simulator,name=iPhone 17 Pro'
make android-build
make android-test
make android-ui-test
```

iOS uses Xcode 26.2+, Swift 6.2+ and XcodeGen 2.46+. Select an installed simulator.
Android uses a Gradle 9.1-compatible launcher JDK, platform/build tools 36 and
compiles with a JDK 17 toolchain. Configure `JAVA_HOME`/`ANDROID_HOME`, or pass
`ANDROID_JAVA_HOME`/`ANDROID_SDK` to generated Make commands. A booted device is
required for `android-ui-test`; `ANDROID_SERIAL` can select an emulator.

Swift branding is in `frontend/swift/apps/ProductApp/Config/Product.xcconfig`;
Kotlin installed identity is in `frontend/kotlin/project/product/product.properties`
and its label in product resource strings. ProductConfiguration and individual
screens own feature behavior. Package names, Kotlin/R namespace and Swift scheme
stay stable while installed branding changes. Replace placeholder icons and
configure release signing before distribution.

Start with an app-owned service port and an injected memory implementation;
return Kernel results and project reads/writes through QueryState/MutationState.
Swift targets must explicitly link imported package products. The
[acceptance probe](acceptance/README.md) demonstrates this in generated output.

With local-data, open Library to create/edit/delete a note. The live count has
its own subscription to the same repository. Delete asks for confirmation. A
failed reload retains a confirmed write receipt and offers refresh. Records
remain after process relaunch; unsaved drafts are temporary. Composition chooses
SQLite and the product-private database location. Inject InMemoryNotesRepository
through ObservableNotesRepository for transient storage. Notes is an example
domain that you can replace, and no source data is seeded.

Selected local module build overlays omit the account/HTTP/session files from
the full reference modules. `swift-test` includes Services/Data; `ios-test`
includes actual form/confirmation/relaunch checks. Android unit tests include
the state owner; device tests cover the product and real SQLite/observation.
Read [the capability contract](../contracts/behavior/local-data-starter.md).

## Copying, diagnostics and provenance

The CLI validates registered source/dependency selections before copying. It
excludes build caches, IDE user state, local SDK paths, environment credentials
and signing files; included source symlinks or special files are refused. Output
must be outside the source checkout. Existing output is never replaced. Failed
generation removes only the new directory it created. Source is left untouched.
`init` does not build, download dependencies, initialize Git or configure secrets.

`mobile-foundry.json` records inputs, generator version, source/profile hashes,
file count and optional Git revision/dirty state. It contains no source absolute
path. Copies do not receive later foundation changes automatically.

`doctor` reports local tool versions/SDK files without installing or building.
It honors explicit `JAVA_HOME`; a stale value must be corrected in your terminal.
Compilation toolchain resolution, simulator availability, signing and an actual
native build remain separate checks. `list --source /other/checkout` and
`init ... --source /other/checkout` select a different registered source checkout.

## Maintenance and extension

Run `make cli-test starter-check notes-check`. CLI tests cover real entry-point
invocation, all three platform copies, identity edits, refusals, exclusions,
source preservation, failure/interrupt cleanup and installation/doctor behavior.
New shell copy checks cover all six platform/profile selections, selected source
isolation, required overlays and incompatible capability refusal. Native output
acceptance is separate; see [readiness](../docs/STARTER-READINESS.md).

[mobile-foundry.starters.json](../mobile-foundry.starters.json) registers profiles
explicitly. [Templates](../templates/README.md) own extraction inventories and
overlays; reusable implementation stays in native apps/packages/modules. The
registry also names optional capabilities and complete supported combination
inventories. The validator reads overlaid build files, rather than interpreting
the source checkout's full-module dependencies as the selected output graph.
Overlays may replace a selected file, but duplicate overlay destinations fail.
Required capability files cannot be excluded. App-specific Notes models/screens
are portable product overlays; foundation ports/adapters remain single-source.
The
validator handles the repository's declared build syntax, not arbitrary Gradle
or Swift programs. A new backend requires a real implementation, combination
rules, generator support and connected acceptance before becoming available.
Adding a placeholder directory or changing a registry status is insufficient.

Read [behavior](../contracts/behavior/project-cli.md),
[construction](../docs/blueprints/project-cli.md) and
[source-copy reasoning](../notes/techniques/local-cli-and-independent-source-copies.md).

CLI 0.7.0 includes Notifications installation metadata in the Go selection. Source copying selects credential resolution v2, the immutable 0002 migration and registry acceptance cases. Native product shells remain independently composed; no OS token/vault hooks or push provider credentials are provisioned.

CLI 0.8.0 selects the Go push-dispatch application foundation, Identity eligibility
capability and conformance cases. The independent output passes check/race/smoke,
disposable PostgreSQL and generated runner acceptance. No gateway, worker or public
send route is enabled. Read [evidence](../docs/blueprints/push-dispatch.md).

CLI 0.9.0 selects optional real APNs/FCM adapters and their private configuration
example. PUSH_CONFIG_FILE plus AUTH_MODE=identity enables the guarded manual
preview route. Source copying prunes .p8, .push-private and known service-account
filenames; generated ignore/setup files match. No key or cloud account is
provisioned. Read [setup and acceptance](../docs/blueprints/push-providers.md).
