# Kotlin foundation

The Android Gradle build is initialized in `project` with a Compose application
and a Kotlin/JVM kernel module. Build and open it using
[Setup](../../docs/SETUP.md).

`project/app` holds the Compose application scaffold and depends on
`project/core/kernel`, `project/core/http`, and `project/core/services`.
The kernel implements typed outcomes, failures, and
public projection under [its shared contract](../../contracts/behavior/kernel.md).
HTTP/services implement [request and health behavior](../../contracts/behavior/http.md),
and the catalog has six injected scenarios. Use Kotlin/JVM libraries for
platform-independent code and Android libraries for Android APIs or Compose.

Keep one Gradle build, its wrapper, and a shared version catalog. Features
and catalog examples compose reusable modules rather than defining duplicate
versions of their components.

Keep shader code and native rendering integration with their graphics module.
Shared graphics fixtures need an explicit Android resource or asset bundling
step. Module ownership is described in [Organization](../../docs/ORGANIZATION.md).

`project/core/data` is an Android library providing native SQLite storage for
Local notes CRUD behind Services ports. Follow [local-data construction](../../docs/blueprints/local-notes.md)
and native notebook reading step 41 for storage, transient drafts and verification.

`project/core/session` provides identity/account values, lifecycle ownership,
swappable authority/vault ports and Android Keystore encryption. **Identity and
session** uses a labeled local demo authority. Follow [session construction](../../docs/blueprints/session.md)
and [Go integration setup](../../docs/blueprints/go-native-session.md) for the
separate real authority, directory and **Go identity and accounts** catalog.
and notebook step 42 for lifecycle ownership.
The same gallery now demonstrates account-scoped local Notes and a labeled
authenticated HTTP probe. core:session provides AuthenticatedHTTPClient; core:data
provides partition factories and lease-bound CRUD. Follow [account construction](../../docs/blueprints/account-access.md)
and notebook step 43 before connecting a real provider.
Services now exposes NotesObserver/Flow; Data supplies process-local SQLite
observation. **Live notes** demonstrates independent current-state readers, and
**Identity and session** includes a lease-owned reader. Follow
[observation construction](../../docs/blueprints/notes-observation.md) and step 44.

[Learning notes](notes/README.md) provide the source reading order, Kotlin
mechanics, framework findings, and verification limits for implemented slices.
