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
    shell = profile["shell"]
    shell_scope = {
        "daily": "The Daily shell contains Today, Library, Activity and Account and a Solid/Glass\nswitch. Library is a placeholder unless local-data was selected.",
        "workspace": "The Workspace shell contains a project list and placeholder detail. At 700\navailable points/dp it shows both panes; native large text prefers one pane.\nSelection survives reflow and compact Back. Replace ProductProject identities\nand WorkspaceScreen with your feature. The header has a Solid/Glass switch.",
        "browse": "The Browse shell contains collection search and placeholder detail. Search is\nlocal, ignores case/surrounding whitespace, and remains when returning from detail.\nReplace ProductCollection identities and BrowseScreen with your feature. The\nheader has a Solid/Glass switch.",
    }[shell]
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
        kotlin_device_checks = ":product:connectedDebugAndroidTest" + (" :core:data:connectedDebugAndroidTest" if local_data else "")
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
    guide += [f"""## Product scope and next feature

{shell_scope}
UI → Query →
Kernel are included with their portable tests/fixtures. Camera, GPU galleries,
account/auth/session providers, backends and synchronization are not selected.
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
        append_backend_documents(destination, inputs, backend, native=True)


def append_backend_documents(destination, inputs, backend, native):
    tasks = {
        "go-dev": "dev", "go-build": "build", "go-check": "check", "go-test": "test", "go-smoke": "smoke",
        "go-db-up": "db-up", "go-db-down": "db-down", "go-db-check": "db-check", "go-migrate": "migrate",
        "go-migrate-check": "migrate-check", "go-integration-test": "integration-test",
    }
    make = "\n.PHONY: " + " ".join(tasks) + "\nhelp:\n" + "".join(f"\t@echo 'make {task}'\n" for task in tasks)
    # Use a separate help target: two help recipes would override native help.
    make = make.replace("help:\n", "go-help:\n", 1).replace(".PHONY: ", ".PHONY: go-help ", 1)
    make += "\n" + "\n".join(f"{task}:\n\t$(MAKE) -C backend/go {child}\n" for task, child in tasks.items())
    makefile = destination / "Makefile"
    makefile.write_text(makefile.read_text() + "\nhelp: go-help\n" + make)
    scope = "Native shells and this API are independent. Authenticated native screens and\nremote repository composition have not been selected in this combination.\n" if native else "This output selects only the API; no native toolchain is required.\n"
    guide = f"""
## Go API

Go module: `{backend.module}`. Requires Go {backend.profile['goVersion']}+.
Selected modules: Health, Identity, Account, Audit and the replaceable Notes
exemplar. Application handlers use consumer-owned ports; memory/PostgreSQL
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
when another API is running. Memory records/sessions/receipts vanish on exit.
Generation does not download modules; the first build may need network access.
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
native offline queues and connected native starter acceptance require deliberate
additional capabilities. The generator records inputs/provenance rather than
provisioning external services or credentials.
"""
    setup = destination / "SETUP.md"
    text = setup.read_text()
    if native:
        text = text.replace("Backend: none.", "Backend: go (independent API).")
        text = text.replace("account/auth/session providers, backends and synchronization are not selected.", "account/auth/session providers and synchronization are not selected in the native shell.")
    setup.write_text(text + guide, encoding="utf-8")
    readme = destination / "README.md"
    text = readme.read_text().replace("Backend: none.", "Backend: go (independent API).")
    readme.write_text(text + f"\nGo API: `{backend.module}`. Run `make go-dev`; use `make go-test` and\n`make go-integration-test` for the included checks. Read SETUP.md for persistence.\n" + ("Native session/data UI wiring is a separate capability.\n" if native else ""), encoding="utf-8")
    (destination / "backend/go/README.md").write_text(f"# {inputs.display_name} API\n\nGo module `{backend.module}`; Health, Identity, Account, Audit and Notes.\n\nRead [project setup](../../SETUP.md#go-api) for runtime, credentials, persistence,\nmigrations and isolated tests. Modules own domain/application ports and adapters;\ncmd/server owns composition. This copy builds without the source checkout.\n", encoding="utf-8")
