# Swift foundation

`apps` holds the iOS catalog application and its feature examples. `packages`
holds reusable Swift Package Manager libraries. The catalog imports the same
library products that future applications will use.

`packages/FoundryKernel` and the `apps/FoundryCatalog` Xcode app are initialized
and linked. The kernel implements typed outcomes, failures, and public
projection under [its shared contract](../../contracts/behavior/kernel.md);
HTTP/services implement [request and health behavior](../../contracts/behavior/http.md).
The app exposes six injected HTTP health scenarios alongside planned areas. Build and open them using
[Setup](../../docs/SETUP.md).

`packages/FoundryData` now provides native SQLite storage for the Local notes
CRUD example behind Services ports. Follow [local-data construction](../../docs/blueprints/local-notes.md)
and native notebook reading step 41 for storage, transient drafts and verification.

`packages/FoundrySession` provides identity/account values, lifecycle ownership,
swappable authority/vault ports and Keychain storage. **Identity and session**
uses a labeled local demo authority. Follow [session construction](../../docs/blueprints/session.md)
and [Go integration setup](../../docs/blueprints/go-native-session.md) for the
separate real authority, directory and **Go identity and accounts** catalog.
and notebook step 42 for lifecycle ownership.
The same gallery now demonstrates account-scoped local Notes and a labeled
authenticated HTTP probe. Session provides AuthenticatedHTTPClient; Data provides
partition factories and lease-bound CRUD. Follow [account construction](../../docs/blueprints/account-access.md)
and notebook step 43 before connecting a real provider.
Services now exposes NotesObserver/NotesSubscription; Data supplies process-local
SQLite observation. **Live notes** demonstrates independent current-state readers,
and **Identity and session** includes a lease-owned reader. Follow
[observation construction](../../docs/blueprints/notes-observation.md) and step 44.

The app's `project.yml` is its XcodeGen configuration. Regenerate the Xcode
project after changing targets, package dependencies, or build settings; keep
the generated project and shared scheme versioned.

Each package owns its manifest, source, tests, and any resources it compiles.
Keep platform shader code with its rendering package. Use shared graphics
fixtures from the root `assets` directory through an explicit resource-loading
or bundling step; runtime code must not depend on the checkout's filesystem path.

Package responsibility names and dependency direction are documented in
[Organization](../../docs/ORGANIZATION.md).

[Learning notes](notes/README.md) provide the source reading order, Swift
mechanics, framework findings, and verification limits for implemented slices.
