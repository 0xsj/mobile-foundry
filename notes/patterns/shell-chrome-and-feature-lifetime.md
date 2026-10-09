# Shell chrome and feature lifetime

Claim: a reusable shell can own layout and native tab chrome while the app keeps
destination identity, feature values and resource lifetime.

Origin/evidence: the 2026-10-09 reserved-UI completion slice extracted AppShell
from the existing five-tab app and added the Shells gallery as a second consumer.
Both native builds pass. Hosted geometry and native tab checks, plus Android
gallery restoration and existing app-shell regressions, establish the observed
behavior below. Source inspection establishes ownership; tests do not measure
physical-device performance or every native navigation gesture.

## What and why

AppShell supplies a passive background, flexible content and independent bottom
navigation region. It does not manufacture a router, scroll view, session or
feature store. The consumer provides a bounded viewport and decides system inset
and keyboard policy. Keeping these decisions visible avoids nested scrolling,
duplicated padding and invisible features holding camera or renderer resources.

Swift TabBar includes page content because native TabView owns its chrome and
safe areas. The shell's external navigation slot stays empty. Compose's bar
contains navigation items only; page content goes into AppShell's flexible slot.
This behavioral parity preserves the native ownership model on each platform.

The feature owns selected identity and page values above these branches. The
Shells preview demonstrates this with Overview/Activity/Settings markers,
spacing and navigation visibility. Recreating or hiding tab chrome does not
erase those app-owned values. Same-ID and unknown fixture selections are no-ops.
The shared bar requires valid unique IDs and a selection included in the list;
it has no fallback policy for removed destinations.

## Example

Add one Overview marker and two Activity markers. Hide bottom navigation,
recreate the Android host, show it again, then revisit Overview. Activity retains
two markers and Overview retains one. Switching the gallery family/theme and
returning to the preview preserves those values and the selected destination.
The existing app shell remains a separate owner: Studio opens/closes the catalog,
and camera/session lifetime stays in the camera feature.

## Gotchas

- Bounded shell slots do not choose what survives leaving a route or process.
  Use app-owned state with intentional saveability, not native view lifetime.
- A floating surface needs a meaningful backdrop. Kotlin AppShell supplies the
  existing Backdrop context; backgrounds are decorative and excluded from
  accessibility. Interactive controls belong in content/navigation.
- Apply system insets once. Native Swift tab chrome owns its insets; Compose's
  TabBar disables Material's internal padding and delegates it to the host.
- Eager stacks are not wrapping or virtualized collections. A vertical
  SectionDivider needs a bounded row height and carries no accessible meaning.
- AppShell does not stop a capture session. Resource shutdown belongs to the
  feature's explicit active/presentation policy.

## Actual use and checks

- [Swift UI walkthrough](../../frontend/swift/notes/modules/packages/FoundryUI/README.md#stacks-separators-and-shells)
  and [app consumer](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#shell-primitives-gallery).
- [Kotlin UI walkthrough](../../frontend/kotlin/notes/modules/project/core/ui/README.md#stacks-separators-and-shells)
  and [app consumer](../../frontend/kotlin/notes/modules/project/app/README.md#shell-primitives-gallery).
- Both builds, 64 iOS app cases, five focused Android shell/gallery cases and
  nine Swift/eight Kotlin UI package cases pass. The Android set includes the two
  existing app-shell regressions. Other Android gallery families were not rerun.

Related: [tab and feature lifetime](tabs-and-feature-lifetime.md),
[component slots](component-slots-and-caller-owned-state.md),
[behavior](../../contracts/behavior/ui-components.md#stacks-separators-and-shells),
[usage](../../docs/blueprints/ui-components.md#stacks-separators-and-shells).

Next questions: which feature values should survive a closed presentation; how
should a deep link reconcile a changed destination list before entering its page;
and when does tablet navigation need another native adapter?
