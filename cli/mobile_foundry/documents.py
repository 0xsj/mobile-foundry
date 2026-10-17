"""Portable selected-platform guides; no source-checkout or machine SDK paths."""
from pathlib import Path


def write_documents(destination, profile, inputs, backend=None):
    if profile is None:
        (destination / "Makefile").write_text(".DEFAULT_GOAL := help\n.PHONY: help\nhelp:\n\t@echo 'Read SETUP.md for the selected Go API.'\n")
        (destination / "README.md").write_text(f"# {inputs.display_name}\n\nIndependent Go API. Read [SETUP](SETUP.md).\n")
        (destination / "SETUP.md").write_text(f"# {inputs.display_name} setup\n\nThis project contains independent backend source copies. No generator or source checkout is required.\n")
        (destination / ".gitignore").write_text(".cache/\nbin/\n.env\n.env.*\n!.env.example\n*.pem\n*.key\n*.p12\n.DS_Store\n__pycache__/\n")
        append_backend_documents(destination, inputs, backend, native=False)
        return
    swift = "swift" in inputs.platforms
    kotlin = "kotlin" in inputs.platforms
    local_data = "local-data" in profile["capabilities"]
    connected = "go-connected" in profile["capabilities"]
    shell = profile["shell"]
    shell_scope = {
        "daily": "The Daily shell contains Today, Library, Activity and Account and a Solid/Glass\nswitch. Library is a placeholder unless local-data was selected.",
        "workspace": "The Workspace shell contains a project list and placeholder detail. At 700\navailable points/dp it shows both panes; native large text prefers one pane.\nSelection survives reflow and compact Back. Replace ProductProject identities\nand WorkspaceScreen with your feature. The header has a Solid/Glass switch.",
        "browse": "The Browse shell contains collection search and placeholder detail. Search is\nlocal, ignores case/surrounding whitespace, and remains when returning from detail.\nReplace ProductCollection identities and BrowseScreen with your feature. The\nheader has a Solid/Glass switch.",
    }[shell]
    if connected:
        shell_scope = "The Daily shell contains Today, Library, Activity and Account. Account owns real\nsign-in, renewal and workspace selection; Library owns remote Notes forms."
    targets = ["help"]
    recipes = []
    guide = [f"# {inputs.display_name} setup\n", "This project contains independent native source copies. The generator and its\nsource checkout are not needed to build, test or run it. Shared libraries keep\ntheir original names; installed product identity is configured separately.\n",
             f"Profile: `{profile['id']}`. Platforms: {', '.join(inputs.platforms)}. Capabilities: {', '.join(profile['capabilities'])}. Backend: none.\n"]
    if swift:
        app = str(Path(profile["platforms"]["swift"]["entry"]).parent)
        scheme = profile["platforms"]["swift"]["scheme"]
        swift_checks = "\n".join("\tswift test --package-path frontend/swift/packages/" + package for package in profile["platforms"]["swift"]["packages"])
        targets += ["ios-generate", "ios-build", "ios-test", "swift-test"]
        recipes += [f"""IOS_TEST_DESTINATION ?= platform=iOS Simulator,name=iPhone 17 Pro

ios-generate:
\tcd {app} && xcodegen generate

ios-build: ios-generate
\txcodebuild -project {app}/{scheme}.xcodeproj -scheme {scheme} \\
\t\t-destination 'generic/platform=iOS Simulator' -derivedDataPath .cache/ios \\
\t\t-configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet build

ios-test: ios-generate
\txcodebuild -project {app}/{scheme}.xcodeproj -scheme {scheme} \\
\t\t-destination '$(IOS_TEST_DESTINATION)' -derivedDataPath .cache/ios \\
\t\t-configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet test

swift-test:
{swift_checks}
"""]
        guide += [f"""## Swift / iOS

Install Xcode 26.2+ (Swift 6.2+) and XcodeGen 2.46+. Select Xcode's command-line
tools. The deployment minimum is iOS 17. From this project root:

```sh
make ios-build
make swift-test
make ios-test IOS_TEST_DESTINATION='platform=iOS Simulator,name=iPhone 17 Pro'
```

Use a simulator installed on your machine. `make ios-build`/`ios-test` regenerate
the Xcode project first. Open `{app}/{scheme}.xcodeproj`, select `{scheme}` and
run on a simulator. Run native build/test jobs serially for the same derived data.

Edit `{app}/Config/Product.xcconfig` for display name, bundle identifier, versions
and your own signing team. The source target/module/scheme stays `{scheme}`.
The generated bundle ID is `{inputs.app_id}`. ProductConfiguration controls the
default material/navigation metadata; separate Sources/Screens files own your features.
Add an AppIcon and deliberate device/distribution signing before publishing.
The simulator targets use ad-hoc signing; they do not verify a release archive.
"""]
    if kotlin:
        kotlin_checks = ":core:kernel:test :core:query:test :core:ui:testDebugUnitTest" + (" :core:services:test :product:testDebugUnitTest" if local_data else "")
        if connected:
            kotlin_checks += " :core:http:test :core:session:testDebugUnitTest :core:services:test :product:testDebugUnitTest"
        kotlin_device_checks = ":product:connectedDebugAndroidTest" + (" :core:data:connectedDebugAndroidTest" if local_data or connected else "")
        targets += ["android-build", "android-test", "android-ui-test"]
        recipes += [f"""ANDROID_JAVA_HOME ?= $(JAVA_HOME)
ANDROID_SDK ?= $(ANDROID_HOME)
ANDROID_ENV = $(if $(strip $(ANDROID_JAVA_HOME)),JAVA_HOME="$(ANDROID_JAVA_HOME)") $(if $(strip $(ANDROID_SDK)),ANDROID_HOME="$(ANDROID_SDK)")

android-build:
\tcd frontend/kotlin/project && $(ANDROID_ENV) ./gradlew :product:assembleDebug

android-test:
\tcd frontend/kotlin/project && $(ANDROID_ENV) ./gradlew {kotlin_checks}

android-ui-test:
\tcd frontend/kotlin/project && $(ANDROID_ENV) ./gradlew {kotlin_device_checks}
"""]
        guide += [f"""## Kotlin / Android

Use a Gradle 9.1-compatible launcher JDK (17–25) and an Android SDK with platform
36, build tools 36.0.0+ and platform tools. Compilation targets JDK 17; Gradle's
toolchain resolver can download that JDK if needed. Android Studio can supply the
launcher JDK/SDK. Set JAVA_HOME and ANDROID_HOME for
your machine, or pass ANDROID_JAVA_HOME and ANDROID_SDK to Make. A local Android
Studio `local.properties` is allowed in this project and is ignored by Git.
The generated project deliberately has no machine SDK configuration.

```sh
make android-build
make android-test
make android-ui-test
```

The last command needs a booted device/emulator; ANDROID_SERIAL can select one.
Open frontend/kotlin/project in Android Studio and choose the `product` run
configuration. The APK is under product/build/outputs/apk/debug. Gradle's checked-in
wrapper downloads its pinned distribution/dependencies when caches are absent;
initialization itself does not download anything.

Edit product/product.properties for installed ID/versions and
product/src/main/res/values/strings.xml for the label. The generated application
ID is `{inputs.app_id}`. Source/R namespace dev.mobilefoundry.product stays stable
when installed identity changes. ProductConfiguration controls initial material/
navigation metadata; separate product screens own your features. Replace the placeholder
launcher icon and add your own release signing. Compile/target SDK is 36; min 24.
"""]
    if local_data:
        guide += ["""## Local data example

Open Library to create/edit/delete Notes and observe the live collection count.
SQLite is the default. Data survives process relaunch in this installed app's
container. Drafts are temporary. Delete asks for confirmation; cancel keeps the
record. Failed/corrupt reads keep stored data and show the existing typed failure.
After a confirmed write, a failed reload retains its receipt; refresh explicitly
rather than repeating the write.

The app's Composition/NotesComposition.swift or notes/NotesComposition.kt selects
the adapter and database location. Change that factory to InMemoryNotesRepository
for transient data, or inject an ObservableNotesRepository into LibraryScreen.
CRUD and observation are separate consumers of the same repository. Screen/
foreground inactivity pauses owned work; resume rereads. The count observes
in-process commits, not external SQL or remote changes.

Selected modules are UI, Query, Kernel, local Services and local Data. Portable
local module manifests intentionally omit account/session/HTTP implementations.
Swift package checks include Services/Data; iOS tests include actual form actions
and process relaunch. Android unit checks include the state owner; device checks
include the product form/confirmation test and real SQLite/observation tests.
Run the commands above serially. Notes is an example domain to replace when
starting your own product, not a required domain for every application.
"""]
    scope_libraries = "UI, Query, Kernel, HTTP, Session and remote Notes Services are included. Camera,\nGPU galleries, offline synchronization and push are not selected." if connected else "UI → Query →\nKernel are included with their portable tests/fixtures. Camera, GPU galleries,\naccount/auth/session providers, backends and synchronization are not selected."
    guide += [f"""## Product scope and next feature

{shell_scope}
{scope_libraries}
Material/tab state is session or
native saved state, not durable settings. Accessibility reductions remain native.

Start a feature in the app's screen folder. Keep its service port and state owner
app-owned; inject a memory/provider implementation, return kernel result/failure
values, and project reads/writes through QueryState/MutationState. Add the domain
and adapter modules deliberately when that feature needs them.
Swift features importing FoundryQuery/FoundryKernel must declare those local
packages and link their products in project.yml application/test dependencies,
then regenerate. Kotlin apps can declare direct core:query/core:kernel dependencies
when using their APIs. Import dev.mobilefoundry.product.R explicitly in screens;
the installed application ID does not rename the source resource namespace.

Go APIs have a separate explicit generator selection. `mobile-foundry.json`
records selected inputs and source provenance. Generation does not initialize Git,
install dependencies, set up secrets or configure remote services.
"""]
    help_lines = "\n".join("\t@echo 'make " + target + "'" for target in targets if target != "help")
    makefile = ".DEFAULT_GOAL := help\n.PHONY: " + " ".join(targets) + "\n\nhelp:\n" + help_lines + "\n\n" + "\n".join(recipes)
    (destination / "Makefile").write_text(makefile, encoding="utf-8")
    (destination / "SETUP.md").write_text("\n".join(guide), encoding="utf-8")
    (destination / "README.md").write_text(f"# {inputs.display_name}\n\nNative {profile['shell']} shell for {', '.join(inputs.platforms)}.\nCapabilities: {', '.join(profile['capabilities'])}.\n\nRead [SETUP](SETUP.md) for toolchains, build/test/run commands and product edits.\nSelected source and fixtures are local copies; no generator/source checkout link is required.\n\nInstalled identity: `{inputs.app_id}`. Backend: none.\nProvenance and generation inputs are in mobile-foundry.json.\n", encoding="utf-8")
    (destination / ".gitignore").write_text(".DS_Store\n.cache/\n.build/\nbuild/\n.gradle/\n.kotlin/\nDerivedData/\nxcuserdata/\n*.xcuserstate\n.idea/\nlocal.properties\n.env\n.env.*\n!.env.example\n*.keystore\n*.jks\n*.p12\n*.mobileprovision\n", encoding="utf-8")
    for platform in inputs.platforms:
        path = destination / Path(profile["platforms"][platform]["entry"]).parent / "README.md"
        if connected:
            path.write_text("# Connected Daily product\n\nAccount provides native registration, explicit Go sign-in, renewal, restore, owned workspace selection, create/rename forms and sign-out.\nLibrary provides account-bound remote Notes CRUD, paging, explicit receipt recovery and read-only saved first pages.\nComposition uses SessionAuthority/CredentialVault and RemoteNotesRepository ports.\nDevice refresh credentials are deployment-bound; pending commands use a private SQLite journal. Account security selects verification/reset/change forms; Library selects scoped live refetch hints.\n\nRead [project setup](../../../../SETUP.md) for the API endpoint, local registration,\nbuilds and independently runnable connected acceptance.\n", encoding="utf-8")
            continue
        if shell != "daily":
            path.write_text(f"# {shell.title()} product\n\n{shell_scope}\n\nThe app owns configuration, ProductState, native navigation and screen content.\nState lives above material/layout branches. Swift keeps it for the window lifetime;\nAndroid restores it through Activity recreation. A fresh launch starts at defaults.\nThese are UI placeholders: no domain provider, disk storage or network is selected.\n\nRead [project setup](../../../../SETUP.md) for branding, native checks and build/run\ncommands. Selected libraries are UI, Query and Kernel with local tests/fixtures.\nAdd deliberate release signing/icons before distribution.\n", encoding="utf-8")
            continue
        if local_data:
            path.write_text("# Daily product with local data\n\nThis product owns its identity, four tabs and editable Notes exemplar. Library\ncontains SQLite-backed create/edit/delete forms and an independent live count.\nComposition selects an ObservableNotesRepository; replace the factory with Memory\nfor transient data. Notes is an example domain to customize. Drafts are temporary;\nrecords remain in this installed product's private container after process relaunch.\n\nSelected libraries are UI, Query, Kernel, local Services and local Data. Account,\nHTTP/session providers, camera, graphics and remote synchronization are not selected.\nThe local build manifests select only the admitted source/tests.\n\nRead [project setup](../../../../SETUP.md) for branding, build/test/run commands,\nsource ownership and included checks. Add deliberate release signing/icons before\ndistribution. Local acceptance does not verify physical-device performance.\n", encoding="utf-8")
            continue
        text = path.read_text(encoding="utf-8")
        marker = "This in-repository entry builds independently"
        if marker in text:
            text = text[:text.index(marker)]
        text += "This is an independent generated source copy. Read [project setup](../../../../SETUP.md)\nfor selected toolchains, branding and build/test/run commands. Generation does not\nverify your release signing, backend integration or physical-device performance.\n"
        path.write_text(text, encoding="utf-8")
    if backend:
        append_backend_documents(destination, inputs, backend, native=True, connected=connected)
    if connected:
        append_connected_documents(destination)


def append_backend_documents(destination, inputs, backend, native, connected=False):
    ignore = destination / ".gitignore"
    ignore.write_text(ignore.read_text() + "\n# Private local object bytes and push provider credentials\nbackend/go/.data/\n.push-private/\n*.p8\npush.local.json\n*service-account*.json\nserviceAccountKey.json\n")
    tasks = {
        "go-dev": "dev", "go-build": "build", "go-check": "check", "go-test": "test", "go-smoke": "smoke",
        "go-db-up": "db-up", "go-db-down": "db-down", "go-db-check": "db-check", "go-migrate": "migrate",
        "go-migrate-check": "migrate-check", "go-integration-test": "integration-test",
        "go-operations-test": "operations-test", "go-prod-config": "prod-config", "go-prod-build": "prod-build", "go-prod-db": "prod-db", "go-prod-migrate": "prod-migrate", "go-prod-up": "prod-up", "go-prod-down": "prod-down", "go-prod-maintenance": "prod-maintenance", "go-prod-backup": "prod-backup", "go-prod-restore": "prod-restore",
    }
    make = "\n.PHONY: " + " ".join(tasks) + "\nhelp:\n" + "".join(f"\t@echo 'make {task}'\n" for task in tasks)
    # Use a separate help target: two help recipes would override native help.
    make = make.replace("help:\n", "go-help:\n", 1).replace(".PHONY: ", ".PHONY: go-help ", 1)
    make += "\n" + "\n".join(f"{task}:\n\t$(MAKE) -C backend/go {child}\n" for task, child in tasks.items())
    makefile = destination / "Makefile"
    makefile.write_text(makefile.read_text() + "\nhelp: go-help\n" + make)
    scope = "Native shells and this API are independent. Authenticated native screens and\nremote repository composition have not been selected in this combination.\n" if native else "This output selects only the API; no native toolchain is required.\n"
    if connected:
        scope = "The selected native apps connect to this API through SessionAuthority,\nAccountDirectory and RemoteNotesRepository. Backend substitution stays at composition.\n"
    guide = f"""
## Go API

Go module: `{backend.module}`. Requires Go {backend.profile['goVersion']}+.
Selected modules: Health, Identity, Account, Audit, the replaceable Notes
exemplar, Files and Notifications. Application handlers use consumer-owned ports; memory/PostgreSQL
adapters stay behind them. No source checkout, local replace directive or module
workspace is needed. External dependencies are pinned in go.mod/go.sum.

{scope}
From this project root:

```sh
make go-build
make go-check go-test go-smoke
make go-dev
```

The development runner explicitly selects owned Identity, with memory storage.
The bare binary defaults to deny authentication; set AUTH_MODE=identity when
launching it directly. HTTP_ADDR defaults to 127.0.0.1:8080. Set a different port
when another API is running. Memory records/sessions/receipts and object bytes vanish on exit.
Generation does not download modules; the first build may need network access.
With PostgreSQL, FILES_DIRECTORY defaults to backend/go/.data/files when run
through Make. Retain this private directory alongside the database; metadata and
bytes are separate stores. FILES_DATABASE_URL optionally overrides DATABASE_URL.
Uploads use authenticated API transfer with a 10 MiB bound; cloud object storage,
background upload, processing and retention jobs are separate capabilities.
Python 3.11+ runs the included smoke/integration scripts.

Register a local user after startup (this example is a development fixture):

```sh
curl -i http://127.0.0.1:8080/v1/identity/register \\
  -H 'Content-Type: application/json' \\
  -d '{{"login":"alex","display_name":"Alex","password":"local example passphrase"}}'
```

Sign in through POST /v1/identity/sign-in, then use its bearer access token for
POST /v1/accounts/bootstrap and scoped reads/writes. Keep tokens/passwords out of
tracked files and logs. Account access is currently owner-only. Notes uses
GET/POST /v1/accounts/{{account}}/notes, version-guarded PATCH/DELETE, and
GET /v1/accounts/{{account}}/note-receipts/{{command}}. Write keys are canonical
UUIDs; retain the exact command and original scope before dispatch. Receipts
survive later edits/deletion. A lost response does not establish rollback.

Completed uploads also deliver an in-app item to the uploader. Use
GET /v1/accounts/{{account}}/notifications (limit/cursor/unread),
POST /v1/accounts/{{account}}/notifications/{{item}}/read with no body, and
GET/PUT /v1/accounts/{{account}}/notification-preferences with
{{"media_in_app":true}}. Read acknowledges once; settings use last committed value.
These two mutations reject Idempotency-Key/If-Match and offer no command receipts.
Notifications uses its own schema/pool; NOTIFICATIONS_DATABASE_URL inherits
DATABASE_URL. Run explicit migrations before API startup after an upgrade.
No native inbox, OS token collection, push provider or retention is selected by default.
Optional real APNs/FCM preview sending requires AUTH_MODE=identity and an explicit
PUSH_CONFIG_FILE (prefer an absolute path to an untracked private configuration).
Copy backend/go/infra/push/example.json into .push-private/push.local.json and
replace its routes/credential paths. Key paths are relative to that config file.
The optional POST /v1/installations/{{installation}}/push-preview requires current
Identity auth, X-Installation-Key, If-Match and {{"preview_id":"UUID"}}. It sends
once, has no idempotency receipt and may duplicate on a repeated request. Default
builds need no provider account or keys. Do not put server credentials in native
apps. Live cloud delivery and native push UI require separate verification.
Queued previews additionally require STORAGE=postgres and PUSH_JOBS_ENABLED=true.
POST /v1/installations/{{installation}}/push-jobs takes the same auth/proof/current
If-Match plus {{"job_id":"UUID"}}; GET the returned ID at that path to inspect it.
The job ID is also its generic payload preview ID. Repeating the identical intent
returns its retained job; a changed owner/revision conflicts. The worker rechecks
the original binding/session and retries only before its durable send marker.
Unknown provider/commit outcomes become uncertain and are never automatically
resent. Apply pending migrations explicitly, including 0004_push_job_recovery.sql
on an existing deployment. POST .../push-jobs/{{job}}/cancel with no body or If-Match
cancels queued/leased work only; it cannot retract a send already marked sending.
POST .../push-jobs/{{job}}/replacement requires current auth/proof/If-Match and
{{"job_id":"NEW_UUID","accept_possible_duplicate":false}} for a failed, blocked,
expired or canceled source. An uncertain source requires true, explicitly accepting
possible duplicate delivery. Accepted/invalid_target/active jobs cannot be replaced.
Exactly one linked replacement is retained per source. Repeating its exact ID,
revision and acknowledgement returns that child; another ID conflicts. GET at the
same /replacement path with auth/proof and no If-Match reads the existing child.
The original outcome is preserved; replacement is a new request, not confirmation
that the earlier send failed. Neither cancellation nor recovery affects descendants.
Jobs expire for execution after fifteen minutes; terminal rows remain retained,
with a ten-thousand-row admission cap. Retention, fanout and cross-owner operations
remain separate. Recovery uses the same explicit queue selection, with no new default worker.

The API also owns a session-bound installation registry. POST /v1/installations
creates metadata with a client UUID and fresh registration_id; GET/PUT/DELETE
/v1/installations/{{installation}} require X-Installation-Key in addition to Bearer
authentication. Generate an independent i1. key from 32 cryptographically random
bytes encoded as unpadded base64url; store it securely on the native installation.
PUT uses a new registration_id/current If-Match; DELETE requires the currently
owning session and version. Lost acknowledgements require current-state readback.
Token/key bytes are never echoed. Identity sign-out/reuse facts retire matching
bindings through the Notifications inbox. This selects registry metadata, not
native OS token capture or APNs/FCM delivery. Explicitly apply migrations on upgrade.

### Durable local storage

Docker Compose uses project `{inputs.name}-api` and its own retained named volume.
The default database port is 58432 on loopback. The database/user are `app`;
the example password is local-development-only. For an additional local project,
set POSTGRES_PORT; the Make database URL follows that port unless DATABASE_URL
was explicitly exported. Never reuse another project's Compose name/volume.

```sh
make go-db-check
make go-db-up go-migrate go-migrate-check
STORAGE=postgres make go-dev
```

Use DATABASE_URL for another owned PostgreSQL server; module-specific overrides
are ACCOUNT_DATABASE_URL, AUDIT_DATABASE_URL, IDENTITY_DATABASE_URL and
NOTES_DATABASE_URL. Migrations are embedded and checksummed. Startup verifies
but never applies them; run go-migrate deliberately. go-db-down retains the
volume. Environment example files are not automatically loaded: export chosen
values in your shell. Store real values in ignored local configuration.

### Isolated acceptance

```sh
make go-integration-test
# Alternative using installed local PostgreSQL binaries:
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/path/to/postgres/bin make go-integration-test
```

The integration runner owns a disposable database and temporary API processes.
It never resets the retained development volume. It checks migration refusal,
atomic writes/receipts, CAS, outbox/inbox delivery and killed API recovery.
Its fixture database/user names are separate from the development stack.

### First domain

Notes is an example domain. Follow internal/notes for domain → app/command/query
→ ports → infra/{{adapter}} → transport/http/v1. Composition is in module.go,
build.go, postgres.go and cmd/server. Modules authorize through capabilities and
react independently through committed facts; do not import another module's
store. Keep numbered migration history immutable. The import-policy check and
tests use your generated module path. Customize this copy independently.

Production auth recovery/verification, membership roles, deployment, Realtime,
native offline queues and additional connected capabilities require deliberate
additional capabilities. The generator records inputs/provenance rather than
provisioning external services or credentials.
"""
    setup = destination / "SETUP.md"
    text = setup.read_text()
    if native:
        text = text.replace("Backend: none.", "Backend: go (connected)." if connected else "Backend: go (independent API).")
        text = text.replace("account/auth/session providers, backends and synchronization are not selected.", "account/auth/session providers and synchronization are not selected in the native shell.")
    guide += """
## Realtime and core identity lifecycle

GET /v1/accounts/{account}/realtime upgrades a native Authorization: Bearer
connection. It sends ready/changed Notes hints with refetch=true and no private
content. Refetch over HTTP after ready/reconnect; hints are coalesced/lossy.
Cookies, browser Origin headers and query credentials are refused. Connections
are bounded and recheck the same bearer/account, so renewal needs reconnect.
The default broadcaster is process-local; run one API replica until you select
a shared feed adapter. It is not a durable change feed or offline synchronization.

POST /v1/identity/email/request accepts email/password under current bearer auth.
POST /v1/identity/email/verify accepts the emailed token. GET /v1/identity/email
reads your verified contact. Recovery does not assume that a login is an email.
POST /v1/identity/password/request-reset accepts email and returns a generic 202;
lookup and SMTP run in a bounded ephemeral queue, with no delivery guarantee.
POST /v1/identity/password/reset accepts token/password. POST
/v1/identity/password/change accepts current_password/new_password with bearer auth.
Both password operations revoke all sessions and invalidate outstanding codes.
Codes are purpose-bound, hashed, single-use and expire after twenty minutes.

Set IDENTITY_MAIL_CONFIG to a private JSON file with Address, From, Username,
Password and Mode (starttls or tls). The production example is under
backend/go/infra/production/mail.example.json. SMTP is off without selection.
Development mode can explicitly use local capture; production refuses plaintext.
No provider credentials are generated/copied. Ordinary acceptance clears mail,
metrics and proxy settings inherited from the host.

## Portable operations

The selected backend includes a non-root scratch Dockerfile, explicit migrations,
health probe, Caddy HTTPS/WebSocket edge, private PostgreSQL/files volumes and
metrics on a separate private listener. No production server is provisioned.
Copy backend/go/infra/production/.env.example to a private .env.production and
configure the database password, public domain, private SMTP file and image tag.
Keep the SMTP file under a private parent directory and readable by the container
user. Review network settings if another stack uses the default subnet.

Run from backend/go after configuring that private environment:

```sh
make prod-config prod-build prod-db prod-migrate
make prod-up
make prod-maintenance
```

prod-up checks schemas and never applies migrations itself. prod-down retains
volumes. METRICS_ADDR is off by default outside Compose; /metrics on its private
listener exposes bounded method/status counters and duration totals without URLs
or identities. POSTGRES_POOL_MAX_CONNS defaults to eight per module (six pools).
TRUSTED_PROXY_CIDRS admits forwarded addresses only from configured peers; Caddy
overwrites X-Forwarded-For. Keep metrics/PostgreSQL/API ports off the public edge.

```sh
make operations-test
make prod-backup BACKUP_DIRECTORY=/private/backups/first
make prod-restore BACKUP_DIRECTORY=/private/backups/first RESTORE_PROJECT=review-copy
```

Backup stops the selected API temporarily, snapshots PostgreSQL and private files,
then restarts it only if it was running. Restore requires a completely unused
project/resource namespace, verifies checksums/archive paths and leaves its API
stopped for review. Use a separate private env file/network for a restore clone.
Backups contain private data; protect/encrypt them for off-host retention.
prod-maintenance prunes only expired identity challenges. Audit, revocation history
and command receipts are retained; broad erasure/retention remains a product policy.
The operations test owns disposable projects, local SMTP capture and restore data.
It does not obtain public certificates or establish live SMTP/APNs/FCM delivery.
"""
    setup.write_text(text + guide, encoding="utf-8")
    readme = destination / "README.md"
    text = readme.read_text().replace("Backend: none.", "Backend: go (connected)." if connected else "Backend: go (independent API).")
    readme.write_text(text + f"\nGo API: `{backend.module}`. Run `make go-dev`; use `make go-test` and\n`make go-integration-test` for the included checks. Read SETUP.md for persistence.\n" + ("Native session/data UI wiring is a separate capability.\n" if native and not connected else ""), encoding="utf-8")
    (destination / "backend/go/README.md").write_text(f"# {inputs.display_name} API\n\nGo module `{backend.module}`; Health, Identity, Account, Audit, Notes, Files and Notifications.\n\nRead [project setup](../../SETUP.md#go-api) for runtime, credentials, persistence,\nmigrations and isolated tests. Modules own domain/application ports and adapters;\ncmd/server owns composition. This copy builds without the source checkout.\n", encoding="utf-8")


def append_connected_documents(destination):
    makefile = destination / "Makefile"
    makefile.write_text(makefile.read_text() + "\nCONNECTED_PLATFORM ?= both\n.PHONY: connected-test\nhelp: connected-help\n.PHONY: connected-help\nconnected-help:\n\t@echo 'make connected-test CONNECTED_PLATFORM=both|swift|kotlin|host'\nconnected-test:\n\tpython3 -B scripts/connected_acceptance.py --platform $(CONNECTED_PLATFORM)\n")
    setup = destination / "SETUP.md"
    setup.write_text(setup.read_text() + """
## Connected native workflow

Start `make go-dev`, then open Account → Create account in the app. The terminal
registration example above is also available. Registration uses IdentityRegistration
through a swappable public port, with one attempt per submit. A confirmed profile
does not sign you in or write credentials. Continue to sign in, enter your password
again, and sign in explicitly. First sign-in bootstraps Personal. Account lists owned
workspaces, lets you select one, renew access, restore saved credentials and sign
out. Library supports remote Notes create/edit/delete and paging. Today/Activity
remain product placeholders and all four outline tabs keep Solid/Glass selection.

The public API base is recorded in mobile-foundry.json and in each app's
ConnectionConfiguration source. Change those app-owned constants for a deployment.
The default is http://127.0.0.1:8080/v1 for Debug. If you selected a different port,
set HTTP_ADDR=127.0.0.1:PORT when starting Go; registration examples must follow it.
For Android emulator/USB use `adb reverse tcp:8080 tcp:8080` (substitute your port),
and remove only your own mapping afterward. Loopback on a physical iPhone is that
phone: use a reachable HTTPS API. Release rejects HTTP; Android's loopback cleartext
exceptions exist only in its Debug resource/manifest. No arbitrary LAN cleartext,
disabled certificate checks or native provider keys are selected.

GoSessionAuthority/HTTPAccountDirectory and HTTPRemoteNotesRepository are selected
at app composition. Replace implementations through their existing ports. The
credential vault uses Keychain on iOS and Keystore-encrypted private storage on
Android, partitioned by deployment and installed app. Restoration renews credentials
and validates the saved account; it never silently selects another account.
Account changes, renewal and sign-out invalidate old HTTP leases and feature owners.

Account → Account security / recover access selects email verification, password
reset and password change through IdentityRecovery. Configure private backend SMTP
before requesting real mail. Passwords/codes are transient and cleared on submit,
background and dismissal. Changes close local access on success or uncertainty;
sign in again or use recovery. The first verified email is the recovery contact;
contact changes and MFA/OIDC/passkeys remain separate workflows.

The registration form accepts canonical ASCII logins of 3–128 characters (initial
letter/digit, then a–z, digits, . _ @ + -); surrounding Go whitespace is trimmed
and ASCII case normalized. Display names trim the same whitespace, admit 1–80
Unicode scalars and refuse controls. Passwords retain exact input: at least 12
Unicode scalars and at most 256 UTF-8 bytes. Draft passwords are transient and
cleared on submit/background/dismissal, never copied into sign-in or saved state.
Login is separate from recovery email. Add a verified recovery contact after
signing in. Duplicate registration cannot change an existing password/profile.
A failed, malformed or interrupted reply may follow creation; try signing in
before explicitly submitting again. There is no registration idempotency key,
automatic retry, durable registration journal or automatic sign-in.

Account → Manage workspaces uses AccountManagement behind the exact session lease.
Refresh reads the current workspace/version; Rename sends its strong If-Match.
Conflicts and unconfirmed writes require another read and explicit review.
Create leaves selection unchanged; close the form and choose Use <name> yourself.
A missing create reply may follow a commit. Review workspace pages and explicitly
acknowledge duplicate risk before another create; listing cannot prove a specific
command outcome. The warning is transient, so review workspaces after an interrupted
process too. There are no Account receipts, automatic retries or durable Account
command journal. Metadata renames preserve Notes scope/pending intent. Names admit
1–80 Unicode scalars after Go whitespace trim, excluding controls.

Library selects AccountLiveHints with native URLSession/OkHttp WebSockets. Ready
and account-scoped changed hints refetch through HTTP; no record data comes from
sockets. Active writes/receipt settlement finish before coalesced reads. Foreground
and lease changes replace connections. Failed subscriptions have at most six
backoff attempts, then manual Reconnect/Refresh; no write or renewal automatically
replays. The local backend hub requires one API replica.
The connected shell selects one iOS scene and a single-task Android entry. Extend
multiwindow support around one application-wide session owner before allowing
independent controllers to write the same rotating credential record.

Writes persist the exact command/input/original deployment, principal and account
in a private, backup-excluded SQLite journal before HTTP. One slot per scope retains
the pending change or last confirmed/refused result (up to 256 scopes, 16 KiB each).
Lost acknowledgements offer Check receipt or Retry same change after process restart;
reads and credential restoration never automatically resend writes. A missing receipt
keeps the change pending. New writes wait for recovery storage to be readable.
Corrupt or unknown stores are kept and refused; no silent reset, expiry or pruning.
Sign-out retains these scoped changes on this device; app removal can erase them.
Unsaved drafts remain temporary. A separate NotesReadCache port selects private,
backup-excluded SQLite first-page snapshots: at most 20 notes per scope, 64 KiB per
snapshot and 32 scopes, oldest snapshots evicted. Snapshots are usable for 24 hours;
expiry is enforced lazily when read, so this is not timed physical erasure. Future
clock timestamps are refused. Sign-out hides cached content and retains disk data;
Library's Remove saved copy deletes only the current account's cached page.
Saved pages are labeled with their saved time and stay read-only until a fresh
server read. Only unavailable/timeout failures fall back; auth refusal hides and
removes the current saved page. Journal checkpoints prevent pre-mutation pages
from reappearing after a changed pending/terminal command. Cache failures are
separate warnings and cannot disable fresh server reads or journal recovery.
Current authenticated access is required: cold credential restoration still needs
the API. Later pages are never cached and cache contents cannot authorize paging
or writes. Multiple queued writes, conflict merging,
background synchronization, Files/inbox screens and live push remain separate
selections. Server data requires PostgreSQL for API restart durability.

### Independent connected acceptance

Stop the development API first. Use an unused loopback endpoint with /v1 for this
local check. The copied driver builds and starts only this project's API, clears
inherited provider/database settings, owns temporary users/data and cleans up its
API, in-memory loopback SMTP/code fixture, fault/WebSocket proxy and ADB mappings. It never resets a retained database. Toolchains/caches and a
booted iOS simulator/Android emulator must be available; ANDROID_SERIAL selects one.

```sh
make connected-test CONNECTED_PLATFORM=host
make connected-test CONNECTED_PLATFORM=swift
make connected-test CONNECTED_PLATFORM=kotlin
```

Host checks run real URLSession/OkHttp HTTP/sockets, SMTP verification/reset/password
revocation, sessions/accounts and Notes receipt/CAS tests
for the platforms present. Device checks additionally execute the actual generated
screens, recovery/verification/password forms, external live writes, renewal,
account isolation, Notes CRUD and secure restoration. iOS terminates
and relaunches the app. Android uses ten instrumentation runs with explicit
force-stops between them, plus Activity recreation. A test-owned HTTP proxy withholds
a committed receipt and refuses an unsent first attempt; process relaunch restores
the same command IDs and inputs for explicit receipt lookup/retry. A separate
account-specific read outage proves native saved-page rendering after process
restart, read-only gating, account isolation, explicit cache removal and fresh
read recovery. Registration additionally creates users through the real forms,
checks duplicate refusal and password clearing, restores a newly registered user,
and loses a committed registration reply before explicitly signing in. The proxy
checks one commit and no automatic registration resend. Workspace checks create,
select, rename, trigger a real competing rename, lose committed create/rename
replies, read/review explicitly and restore selected-account Notes isolation.
Host and screen scenarios use separate owned API processes so their authentication
budgets do not affect each other; server guards are preserved. No product debug hook or
automatic replay is selected. Reports stay under .cache and
product/build. Android's install-once instrumentation receipts stay under
.cache/connected-android; its driver preserves private app data between processes.
UI acceptance is opt-in and skipped by ordinary native tests without
this owned API; connected-test requires every selected scenario to execute with
zero failures/skips. This validates local Debug workflows, not release signing,
cloud delivery, offline sync or physical-device performance.
""")
