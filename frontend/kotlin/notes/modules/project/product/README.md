# Kotlin product walkthrough

Claim: a separate Android product module can own native entry, saved tab/theme
state and branding while sharing UI/Query/Kernel with the catalog.

## Origin and files

Added 2026-10-10, JDK 17 / AGP 9.0.1 / Kotlin 2.3.20 / Compose BOM 2026.03.01.
Compile/target SDK is 36, minimum SDK 24.

| Source | Responsibility |
| --- | --- |
| [module build](../../../../project/product/build.gradle.kts) | Separate app, UI dependency, product properties and instrumented test wiring. |
| [product.properties](../../../../project/product/product.properties) | Installed application ID/version; Kotlin/R namespace is independent. |
| [manifest](../../../../project/product/src/main/AndroidManifest.xml) and [resources](../../../../project/product/src/main/res) | Launcher, label, outline tabs and simple product icon; no camera/internet permission. |
| [MainActivity](../../../../project/product/src/main/kotlin/dev/mobilefoundry/product/MainActivity.kt) | Native edge-to-edge entry and runtime resource branding. |
| [configuration](../../../../project/product/src/main/kotlin/dev/mobilefoundry/product/ProductConfiguration.kt) | Initial material and tab metadata. |
| [ProductRoot](../../../../project/product/src/main/kotlin/dev/mobilefoundry/product/ProductRoot.kt) | Saved selected identity/theme above theme/content branches; native tab callbacks and one inset owner. |
| [screens](../../../../project/product/src/main/kotlin/dev/mobilefoundry/product/screens) | Distinct placeholder features and app-owned page presentation. |
| [native tests](../../../../project/product/src/androidTest/kotlin/dev/mobilefoundry/product/ProductEntryTest.kt) | Real Activity entry, all tab interactions, material retention/recreation and installed branding/permissions. |

## Flow and customization

MainActivity reads app_name; ProductRoot creates two rememberSaveable owners and
projects theme/navigation. Changing material does not replace the owners. Only
known ProductTab IDs are admitted. Each page scrolls; AppShell receives a bounded
viewport, and root composition consumes safeDrawing and IME insets once. The
floating TabBar has a passive AppShell backdrop and outlined app-owned icons.

Edit product.properties for installed identity/versions and strings.xml for
display name. Keep dev.mobilefoundry.product as the source/R namespace unless
intentionally refactoring source. Shared component/library names are not branding
inputs. The Gradle settings overlay selects only product/ui/query/kernel for
future extraction; current repository settings register both apps.

Replacing TodayScreen with a feature-owned query/mutation workflow is an example
extension. The product has no session/credential namespace or backend adapter.

## Verification and limits

Debug application and instrumented APK assembly pass. Two real MainActivity
checks pass on API36_Test / Android 16 with zero failures/skips: initial branding,
all four actual tab selections, toggling Glass while Account stays selected,
Activity recreation with selection/material retained, return navigation, installed
label and absence of camera/internet permissions. Fresh process/launch preference
persistence, TalkBack, physical-device performance and release signing are separate.

`make starter-check` validates current source/fixtures/closed module selection;
negative mutation probes remove Query, UI fixtures and safe relative paths. No
CLI or independently generated product was verified by that initial entry slice.

Launch captures were inspected after reinstalling the APK removed by the native
test runner. The first manual capture contained a System UI timeout dialog.
ActivityManager recorded System UI KeyguardService and keyboard startup ANRs at
16:47, before the 16:49 test execution and 16:54 product relaunch. Selecting Wait
cleared the dialog; a new capture shows the product's normal Today page and four
outline tabs. This is emulator interruption evidence, not a measured device
performance result or a verified product ANR. iOS's actual launch capture was
also inspected. Captures/logs remain in ignored `.cache/product-entry-*` artifacts.

## Independent CLI output

Observed 2026-10-10 using mobile-foundry 0.1.0, Gradle 9.1.0 / AGP 9.0.1 /
Kotlin 2.3.20 / Compose BOM 2026.03.01; launcher JDK 21.0.6 with compilation
toolchain 17, API36_Test / Android 16. Kotlin Sprout (Kotlin-only) and Orbit Lab
(both) were generated under `/private/tmp/mobile-foundry-cli.nQJrnN`, with their
own product-only settings, wrapper, modules, fixtures and new installed labels/IDs.
Both debug apps build, launch and have inspected captures. Kotlin Sprout passes
two actual Activity cases; Orbit Lab passes three including the
[first-feature probe](../../../../../../cli/acceptance/README.md). XML contains
zero failures/errors/skips. Copied Kernel 9 / Query 3 / UI 8 checks pass.

The added memory service returns Kernel results. The injected CounterModel
settles Query/Mutation snapshots, and real Increment taps render content and a
confirmed receipt. The screen's R import is explicit; installed branding does
not rename the resource namespace. APK badging and runtime checks confirm new
identities. A subsequent `--no-build-cache --rerun-tasks` build of Kotlin Sprout
passes all 58 executed tasks, distinguishing reused build-report paths from
actual copied inputs. No local.properties or source-checkout link is required.
Doctor exposed a stale JAVA_HOME and was corrected to distinguish launcher
compatibility from the declared compilation toolchain; read
[the toolchain note](../../../substrate/gradle-and-android-bootstrap.md#gradle-launcher-and-compilation-toolchain).

This establishes the local Daily starter. Durable preference/data, connected
providers, older OS/devices, release signing and full accessibility remain separate.

## Optional local data output

Added 2026-10-10, CLI 0.2.0. The [Kotlin overlays](../../../../../../templates/overlays/local-data/kotlin)
select local Services/Data, add native tests/assets, and replace Library with an
app-owned Notes exemplar. The in-repository product remains the UI-only default.
Installed ID and label are still product configuration, not Kotlin/R namespace.

Read NotesComposition → LibraryScreen → LocalNotesViewModel and LiveNotesReader.
Composition alone chooses SQLite/local-notes.sqlite. The view model owns native
TextFieldState, draft admission, one request job and Query/Mutation snapshots.
Saving reads current native input synchronously; collection does not write old
emitted strings back into editing state. Lifecycle.repeatOnLifecycle(STARTED)
starts a separate reader, reloads the writer and pauses both on stop/disposal.
The screen collects StateFlows with lifecycle awareness and owns confirmation.

QueryState.value/failure are extension properties, so importing only isSubmitting
fails compilation even with the right module dependency. Explicit query imports
fixed the first generated compile. The local module manifests omit HTTP/session/
account source rather than copying unused provider dependencies. The foundation
ports/SQLite/invalidation code remain sourced from their original module owners.
Default namespace and minimum SDK remain unchanged; observed runtime is API36.

Included unit cases exercise native input admission, late commits and retained
receipts. Product device cases exercise form/confirmation/recreation, and Data
device cases exercise actual SQLite/cross-instance observation/corruption refusal.
Activity recreation alone is not process death; [readiness](../../../../../../docs/STARTER-READINESS.md)
records the separate relaunch evidence and actual results.

Study next: what does an active subscriber retain when a write notification is
coalesced, and which owner cancels work when a tab closes versus an Activity stops?

Observed acceptance: Field Ready (both) and Kotlin Ready (Kotlin-only), generated
under `/private/tmp/mobile-local-data-k1y9qusq`, each build and pass 26 unit/eleven
device cases with zero failures/errors/skips. The initial form locator expected
Enter a title rather than the native supporting text Error: Enter a title;
correcting the assertion resolved the failure. Unique test records and a relative
count preserve existing records. Separate native input created Process rerun
persisted; force-stop removed its PID, fresh launch and Library displayed that
record/current count. The screenshot was inspected. System UI's earlier startup
ANR was cleared with Wait, with am_anr identifying that process rather than the
product. Full Services passes eleven regressions. Device/provider/release and
backup/restore behavior are not established by this emulator acceptance.

## Workspace and Browse output

Added 2026-10-10, CLI 0.3.0. The in-repository :product remains Daily. Explicit
Workspace/Browse inventories copy a different app source set into the stable
product module/Kotlin namespace, with configured installed identity/label.

Read [shared root/configuration](../../../../../../templates/overlays/shells/kotlin),
then [Workspace state/screen/tests](../../../../../../templates/overlays/workspace/kotlin)
or [Browse state/screen/tests](../../../../../../templates/overlays/browse/kotlin).
ProductRoot uses rememberSaveable(stateSaver = ProductState.Saver) around an
immutable state value above FoundryTheme and screen/layout branches. listSaver
encodes stable ID strings and primitive search/flag/material values, rather than
depending on enum ordinal or saving provider objects. Restoration resolves the
identity against current entries; Workspace requires a valid identity before
restoring its detail flag. This is Activity state restoration, not a database.

Workspace passes selection and admitted actions into bounded SplitPane slots.
The primitive handles available width/font scale; compact detail owns an enabled
BackHandler. Expanded list and detail coexist, including an empty prompt before
selection. Browse owns trimmed case-insensitive filtering and keeps search when
Back clears selection. Native focus/keyboard close before opening detail. System
Back returns to the current list before exiting. The root consumes safeDrawing
and keyboard insets once; screen bodies scroll within DetailShell.

The inventories omit Daily screens/icons/tests and catalog fixtures/actions.
Native tests cover real Activity search/navigation/material/recreation and
Workspace bounded width/large-text changes. The first expanded assertion matched
Project two in both panes; scoping it to detail resolves the ambiguous locator.
That failure did not indicate a lost selection. [Readiness](../../../../../../docs/STARTER-READINESS.md)
records independent generated-output evidence and remaining release/device limits.

Observed acceptance completed 2026-10-11: Kotlin-only and combined outputs for
both shells build independently. Workspace passes four device cases per output;
Browse passes three, with zero failures/errors/skips. Shared Kernel/Query/UI
checks pass 9/3/8. API36_Test / Android 16 runs actual navigation, search/clear,
material, Activity recreation and system Back; bounded pane tests check 800→390dp
and font scale 1.5. List/detail captures were inspected. This establishes UI shell
readiness, not records/providers, durable preferences, release or physical devices.

Study next: what happens if a restored identity is no longer admitted, and why
should a provider revalidate it even when saved navigation restoration succeeds?

## Related reading

Read [Gradle product identity](../../../substrate/gradle-and-android-bootstrap.md#clean-product-identity),
[product extraction ownership](../../../../../../notes/patterns/product-entry-and-extraction-closure.md)
and [starter readiness](../../../../../../docs/STARTER-READINESS.md).
Next: how should CLI output select a new application ID while preserving source
namespace and portable Gradle/fixture wiring?

## Connected Daily output

CLI 0.12.0 selects [connected product overlays](../../../../../../templates/overlays/go-connected/kotlin)
only with Daily + go-connected + Go. The source :product remains UI-only. Read
ConnectionConfiguration → ConnectedViewModel/ConnectedSessionModel → ProductRoot →
AccountScreen/LibraryScreen. Product-only settings select HTTP/Session and a narrow
remote Services closure; no catalog app, local SQLite, provider SDK or push worker
is required by the native product.

The Activity ViewModel owns one controller across recreation and constructs the
Keystore vault from Application. [Lifetime/networking reasoning](../../../substrate/connected-product-lifetime.md)
explains the framework mechanics. ConnectedSessionModel remains a plain class with
injected scope/authority/vault/transport. It collects session state, detaches before
session actions, then binds each new exact lease. Foreground return revalidates
expiry/refetches; no automatic renewal or write replay is selected. Password input
uses remember rather than rememberSaveable and clears on submit/epoch changes.

RemoteNotesStore supplies app-owned paging, draft/receipt and pending-command state
behind RemoteNotesRepository. Its three unit cases cover lost response through
renewal, confirmed-write/read failure and late completion after account replacement.
The source contract/repository remain shared; this product does not depend on the
catalog's UI owner. In CLI 0.12.0 pending commands were process-local; the follow-up below adds disk recovery.

[The copied driver](../../../../../../scripts/connected_acceptance.py) runs two
instrumentation stages against its own copied API. It explicitly force-stops the
installed app between stages; the second must restore the selected workspace and
fetch the server note, then verify account isolation/deletion/sign-out. Recreation
is tested separately. [Construction/evidence](../../../../../../docs/blueprints/go-connected-starter.md)
and [readiness](../../../../../../docs/STARTER-READINESS.md) record the observed gate.
Study next: why is a retained ViewModel insufficient for recovering a pending write
when a new process successfully restores its refresh credential?

## Durable recovery follow-up — 2026-10-17

CLI 0.13.0 injects SQLitePendingNotesStore into connected product composition.
RemoteNotesStore loads the journal before enabling writes, stages before HTTP and
settles the receipt before clearing pending. Storage failure requires reload; a
missing receipt keeps pending. Bind/refresh/process restore never automatically
resends. Five owner cases include a newly constructed owner, lost stage acknowledgement,
failed receipt persistence and a late reply after account replacement.

Read [pending construction and actual process evidence](../../../../../../docs/blueprints/pending-notes.md)
and [intent-before-effect reasoning](../../../../../../notes/patterns/persist-intent-before-external-effect.md).
The generated selection copies only the journal adapter from Data; unsaved drafts
remain temporary and the catalog's recovery owner is independent. Next: a product
read cache/offline launch needs an explicit policy beyond durable command input.


## Identity forms and live Notes — 2026-10-17

Claim: the product owns secret drafts, uncertain credential transitions and
foreground reconnect policy; shared services own protocol seams. Origin: CLI 0.15.0.
The go-connected overlays add IdentitySettingsModel and native security forms.
Passwords/codes exist only in transient view state/submitted work and clear on
submit, epoch change, background and dismissal. Dismissing an in-flight password
change closes local access. Generation checks suppress its late reply even after
a replacement session signs in; storage-clear failure retains existing refusal rules.

Conceptual: a failed response after password change cannot tell the app whether
the server changed the credential. It signs out locally and asks for a new sign-in
or explicit recovery instead of retrying a mutation.

ConnectedSessionModel binds account live hints to the current lease and foreground
lifetime. Six attempts with bounded backoff/jitter precede manual Reconnect. Notes
coalesces hints behind reads/writes and never replays a pending change. Actual
product acceptance checks external writes, email codes and post-recovery sign-in,
separately from prior process-restore checks. Read [setup and dated evidence](../../../../../../docs/blueprints/native-identity-live.md) and [read/write ordering](../../../../../../notes/patterns/live-invalidation-and-pending-writes.md).
Next: choose registration/contact-change policy or read caching deliberately; no
provider/deployment or automatic synchronization follows from these screens.

## Read-only remote snapshots — 2026-10-17

Claim: cached presentation must stay distinct from authoritative loaded state. Origin: connected read-cache slice. Read [owner overlay](../../../../../../templates/overlays/go-connected/kotlin/RemoteNotesStore.kt) and [owner checks](../../../../../../templates/overlays/go-connected/kotlin/RemoteNotesStoreTest.kt).

The product restores its journal before a saved page, compares command/status checkpoints and labels cached content with saved time. It can show that page before HTTP completes and on unavailable/timeout results; only a fresh page plus readable journal enables writes. Auth refusal hides content and suppresses fallback for that binding. Cache warnings never replace successful HTTP content. Remove saved copy affects only this account; sign-out cancels publication and hides content immediately.

Gotchas: the 24-hour window controls usability with lazy cleanup, not scheduled erasure. Private backup-excluded storage is not an offline authentication grant. Later pages/cursors and automatic write replay remain outside this capability.

Actual verification and versions are tracked in [construction/evidence](../../../../../../docs/blueprints/notes-read-cache.md). Continue with [checkpoint reasoning](../../../../../../notes/patterns/cached-content-and-command-checkpoints.md). Next: what product requirement would justify offline startup or a multi-page synchronization cursor?

## Native registration — 2026-10-17

Claim: onboarding navigation and credential issuance need separate owners and outcomes. Origin: connected registration slice. Read [presentation owner](../../../../../../templates/overlays/go-connected/kotlin/RegistrationModel.kt) and [owner checks](../../../../../../templates/overlays/go-connected/kotlin/RegistrationModelTest.kt).

Account opens a separate registration sheet, avoiding mixed sign-in/new-password field grouping. The model owns busy/outcome/generation/epoch, while the form owns a transient password. Successful submission disables further submit until a newly opened form. Continue passes only the login hint; sign-in needs password re-entry. Current authenticated binding clears obsolete registration state. Background/dismissal invalidates pending publication and warns that creation may still have committed.

Gotchas: duplicate login is not ownership proof; cancellation is not rollback; this endpoint has no command receipt/idempotency key. Never put registration passwords into the Notes journal/cache or replay them on restore. The native ASCII login subset is deliberate and tested, rather than inferred from platform lowercase/trim defaults.

Actual verification and versions are recorded in [construction/evidence](../../../../../../docs/blueprints/native-registration.md). Continue with [profile versus session](../../../../../../notes/patterns/registration-is-not-session-issuance.md). Next: where should a product place invitations, verified-contact requirements and approval before granting an account?


## Owner workspace follow-up — 2026-10-17

Claim: metadata editing uses versioned observations without silently changing
selected data scope. Origin: the CLI 0.18.0 workspace slice. Read
[construction and independent evidence](../../../../../../docs/blueprints/native-workspaces.md), then
[the source](../../../../../../templates/overlays/go-connected/kotlin/WorkspaceModel.kt) and
[the focused checks](../../../../../../templates/overlays/go-connected/kotlin/WorkspaceModelTest.kt).

AccountName counts Unicode scalars and retains exact normalization. The provider
read/create/rename port is swappable; its Go adapter adds UUID/owner/strong-ETag
and exact command confirmation. Product owners require a fresh read before rename,
block queued double submissions, and retain interrupted-create uncertainty before
cleanup. A fresh page plus explicit duplicate-risk choice enables a new create;
it does not prove the earlier command's outcome. Rename changes display metadata,
not a lease, vault or pending Notes checkpoint. Drafts/warnings are process-local.

Conceptual flow:

```text
current observation → If-Match rename → conflict → fresh read → user review
```

Six owner cases per native platform cover input, duplicate submit, read gates,
uncertain creation, generation replacement and exact-lease rejection. Boundary
and actual Go port tests establish their separate guarantees; device evidence and
limits are recorded in the construction guide. See
[current state versus command receipts](../../../../../../notes/patterns/current-state-is-not-a-command-receipt.md). Next: should this product
require durable Account command receipts or membership/invitation policy?
