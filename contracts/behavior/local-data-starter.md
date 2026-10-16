# Optional local data starter

`mobile-foundry init NAME --platform swift|kotlin|both --with local-data`
selects the registered Daily/UI/local-data combination. Omitting `--with` keeps
the empty UI starter. Unknown, repeated or unregistered capability combinations
are refused before output creation. Selection never discovers modules implicitly.

Local data adds Notes as an app-owned exemplar, not a required domain for future
products. Library contains create/edit/delete forms and a saved collection;
Today, Activity and Account retain their shell roles. The app composition injects
an ObservableNotesRepository into separate CRUD and live-reader state owners.
SQLite is the default; developers can inject InMemoryNotesRepository through the
same port. No data is seeded and no networking/session/account provider is added.

The [local Notes](local-notes.md), [observation](notes-observation.md),
[query UI](query-ui.md) and [mutation](forms-mutations.md) contracts control
admission, receipts, failure values, cancellation and snapshot lifetime. Initial
reads gate editing. Deletes require confirmation; cancel has no write effect.
Committed writes reload before actions resume. A failed read retains the receipt
and previous content, offers explicit refresh, and never replays a write.

The live reader independently displays the current saved-note count. It observes
commits from other instances in this process. Screen/foreground inactivity pauses
observation and owned requests; resume rereads. Drafts are temporary; records
survive process relaunch in the same installed container. Swift stores them in
Application Support/LocalNotes/notes.sqlite; Kotlin uses local-notes.sqlite inside
the product's database directory. App IDs provide separate installed containers.
Corrupt/unsupported stores remain intact and return the existing typed failures.

The combination inventory copies selected Services/Data source and tests, shared
fixtures, and portable app/build overlays. Local module overlays retain the
existing module names and omit HTTP/session/account implementations and their
dependencies. HTTP Notes adapters live in separate source files so local domain
ports have no transport import. Default/catalog package manifests remain full.
Validation reads effective overlaid manifests and checks dependency, source,
fixture, settings and overlay closure before exclusive output creation.

Generated setup/Make targets include selected Services/Data checks and product
checks. Metadata records the selected capabilities and the exact combination
manifest/content digest. The output needs neither the CLI nor source checkout.
Standalone readiness requires independently generated native builds/runs, CRUD,
validation, canceled/confirmed deletion, observation and process-relaunch evidence.
Remote sync, account migration, backup/export and release/device guarantees are
outside this local capability.
