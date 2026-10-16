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
