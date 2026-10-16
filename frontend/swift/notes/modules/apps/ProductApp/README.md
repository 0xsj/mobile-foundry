# Swift ProductApp walkthrough

Claim: the product app owns window navigation/configuration and imports shared
UI without importing catalog examples.

## Origin and files

Added 2026-10-10, Swift 6.2.3 / Xcode 26.2 / XcodeGen 2.46.0 / iOS 17 minimum.

| Source | Responsibility |
| --- | --- |
| [project.yml](../../../../apps/ProductApp/project.yml) | Separate application/test target and ProductApp scheme; only direct library dependency is FoundryUI. |
| [Product.xcconfig](../../../../apps/ProductApp/Config/Product.xcconfig) | Product display/bundle identity, versions and initially blank developer team. |
| [ProductApp](../../../../apps/ProductApp/Sources/ProductApp.swift) | WindowGroup entry; ProductEntry keeps state per window above the theme. |
| [configuration](../../../../apps/ProductApp/Sources/ProductConfiguration.swift) | Runtime bundle label, default glass, typed tab identities and admitted selection. |
| [root view](../../../../apps/ProductApp/Sources/ProductRootView.swift) | Scoped theme, passive background and native shared TabBar. |
| [screens](../../../../apps/ProductApp/Sources/Screens) | Product-owned Today/Library/Activity/Account placeholders; Account owns theme switch presentation. |
| [tests](../../../../apps/ProductApp/Tests/ProductEntryTests.swift) | Actual native tabs/switch binding and built identity/permission metadata. |

## Flow and customization

ProductEntry creates state; ProductRootView binds it through the themed subtree.
Known tab selection maps to a typed ProductTab; unknown IDs are ignored. Theme
changes keep the state owner and selected destination. AppShell supplies layout,
while native TabView owns navigation safe areas. Screens scroll independently.

Edit the product xcconfig for branding rather than changing UI package source.
Runtime display name reads the built CFBundleDisplayName. The target/module/scheme
remains ProductApp when an installed name/bundle ID changes. No camera permission,
developer team or credential namespace is inherited. Add your AppIcon and signing
configuration for distribution.

An example change is replacing TodayScreen's placeholder with a feature-owned
service/query composition; configuration and native tabs need no shared-library
changes. The minimal profile does not include Data/Session/domain providers.

## Verification and limits

`make product-ios-generate` and the simulator test build pass. Two hosted checks
pass on iPhone 17 Pro / iOS 26.2 with zero failures/skips: actual four-item UITabBar,
all selected destinations, actual UISwitch event into the binding, theme retention,
unknown ID admission and built non-catalog metadata with no camera description.
The tests drive tab selection through the root binding; they do not simulate all
physical tab touches. Runtime preferences are not persisted across fresh launch.
An actual product launch capture was also inspected for configured display name,
floating placeholder panel and four native outline tabs; its artifact is
`.cache/product-entry-ios.png`.
That initial entry slice did not verify independent generation. App Store metadata,
physical-device accessibility and full iPad layouts remain outside its checks.

`make starter-check` validates the candidate's source/fixture closure. The profile
preserves Swift package/test path depth and regenerates its Xcode project; no
source-checkout link may be required by later generated output.

## Independent CLI output

Observed 2026-10-10 using mobile-foundry 0.1.0, Xcode 26.2 / Swift 6.2.3 /
XcodeGen 2.46.0 and iPhone 17 Pro / iOS 26.2. Swift Sprout (Swift-only) and Orbit
Lab (both) were generated under `/private/tmp/mobile-foundry-cli.nQJrnN` with new
bundle/display inputs. Each builds from its own generated Makefile and local
packages/fixtures. Swift Sprout passes both hosted native entry cases; Orbit Lab
passes three after adding the [temporary first feature](../../../../../../cli/acceptance/README.md).
Results contain zero failures/skips. The copied Kernel 7 / Query 3 / UI 9 checks
pass. Built plists and inspected launch captures show the configured identities.

CounterModel receives a memory service through its port, returns Kernel results
and settles QueryState/MutationState; its check verifies a successful receipt,
reread, shared provider and independent provider. Today renders the actual feature.
Adding explicit Query/Kernel package links fixed its initial linker rejection;
the profile itself still imports only UI. See
[package wiring](../../../substrate/swift-package-and-xcode-project-wiring.md#independent-source-copies).
The iOS entry checks drive tab bindings/native switch events rather than every
physical tab gesture. This establishes the local Daily starter, not connected
providers, persistence, release signing or physical-device behavior.

## Optional local data output

Added 2026-10-10, CLI 0.2.0. `--with local-data` selects a complete combination
inventory. The [Swift overlays](../../../../../../templates/overlays/local-data/swift)
add local Services/Data package links and an editable Library example; the
in-repository UI-only ProductApp remains minimal.

Read NotesComposition → LibraryScreen → LocalNotesStore and LiveNotesStore.
Composition chooses Application Support/LocalNotes/notes.sqlite and injects an
ObservableNotesRepository. @State retains reference owners; @Observable projects
their changing values. The CRUD owner validates the title, admits one operation,
retains commit feedback and reloads. The independent reader owns an explicit
subscription/cancel task and displays current count. Library owns focus/delete
confirmation and scene/view lifetime; disappearing/background pauses owners.
The adapter owns no UI lifetime or database handle inside state.

Reduced local module manifests copy the original guest SQLite, ports and
notification code. Account/session/HTTP files are not selected. HTTPNotesService
has its own source file in the full Services module, preserving existing API.
Hosted state checks exercise memory/SQLite, late commits and failed post-commit
reads; included UI checks drive real form/confirmation actions and process
relaunch. Read [capability behavior](../../../../../../contracts/behavior/local-data-starter.md)
and [readiness](../../../../../../docs/STARTER-READINESS.md) for actual execution
and limits. Native minimum remains iOS 17; the observed simulator is iOS 26.2.

Study next: why can a successful receipt coexist with a failed read, and why
does canceling a scene's task not establish rollback of an already committed row?

Observed acceptance: Field Ready (both) and Swift Ready (Swift-only), generated
under `/private/tmp/mobile-local-data-k1y9qusq`, each build and pass eight native
cases with zero failures/skips. The UI workflow includes termination/relaunch
before confirming deletion, and checks the live count relative to pre-existing
records. Its exported relaunch capture was inspected. Selected package checks
pass 30 cases; full Services passes eleven. This verifies independent local data,
not release, provider, backup/restore or physical-device behavior.

## Workspace and Browse output

Added 2026-10-10, CLI 0.3.0. ProductApp remains the in-repository Daily reference;
the registered Workspace/Browse inventories select different app sources into
the same stable target/scheme. Installed branding still comes from xcconfig.

Read the [shared root/configuration](../../../../../../templates/overlays/shells/swift),
then [Workspace state/screen/tests](../../../../../../templates/overlays/workspace/swift)
or [Browse state/screen/tests](../../../../../../templates/overlays/browse/swift).
ProductEntry's @State remains the window owner. ProductRootView binds it above
FoundryTheme; the fixed header displays product branding and Glass. DetailShell
bounds the content; each body region supplies scrolling. Shared SplitPane applies
available width and Dynamic Type policy, while Workspace owns select/back.

Workspace keeps a typed optional ProductProject and a separate detail flag.
Before selection, an expanded layout displays an empty detail prompt. Compact
Back clears the flag, retaining identity; selecting the same project reopens it.
Browse keeps optional ProductCollection and search text, computes visible titles
from trimmed case-insensitive search, and clears only selection on Back. Its
FocusState ends editing before detail opens. Invalid IDs cannot replace selection.

The inventories select only ProductApp.swift/configuration plus the chosen
root/state/screen/tests; Daily screens and catalog values are omitted. Reusable
UI/Query/Kernel stay canonical. The generated app tests include actual native
touches, switch changes, navigation and captures; metadata checks inspect the
built app. [Readiness](../../../../../../docs/STARTER-READINESS.md) records actual
independent acceptance, toolchains and limits.

The first automation run showed a full labeled Toggle accessibility row whose
center tap did not change the switch. A trailing-edge native control tap does.
A container identifier also masked a descendant Back identifier; identifiers now
belong to actionable buttons. These corrections concern test targeting and
accessibility projection, not a new shell navigation rule.

Observed acceptance completed 2026-10-11: Swift-only and combined outputs for
both shells each build and pass three native cases with zero failures/skips.
Workspace runs on iPad Pro 11-inch (M5) and iPhone 17 Pro / iOS 26.2; Browse runs
on iPhone. Native search/clear/back, switch changes and rotation/expanded panes
are exercised. Exported captures were inspected. Copied UI/Query/Kernel checks
pass 9/3/7. This establishes standalone UI shell readiness, not durable settings,
providers, release signing or a physical-device performance budget.

Study next: why does Workspace keep selection on Back while Browse clears it,
and where should a real provider validate a restored detail identity?

## Related reading

Read [build wiring and product identity](../../../substrate/swift-package-and-xcode-project-wiring.md#clean-product-identity),
[product extraction ownership](../../../../../../notes/patterns/product-entry-and-extraction-closure.md)
and [starter readiness](../../../../../../docs/STARTER-READINESS.md).
Next: how should generated output select its own bundle ID/team and verify a
first feature while preserving reusable API names?
