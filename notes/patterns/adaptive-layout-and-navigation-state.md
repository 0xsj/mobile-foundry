# Adaptive layout and navigation state

Claim: changing visible panes should change presentation without repairing
feature identity, replaying navigation or discarding edits.

Origin/evidence, 2026-10-09: the Workspace component batch, source inspection,
native hosted layout and Android interaction/restoration checks. Read the
[contract](../../contracts/behavior/ui-components.md#adaptive-workspaces),
[Swift app walkthrough](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#workspace-gallery)
and [Kotlin app walkthrough](../../frontend/kotlin/notes/modules/project/app/README.md#workspace-gallery)
for concrete sources, completed checks and limits.

## What and why

Keep selected identity and compact detail intent separate. A wide workspace can
show a selected project beside its list, while a phone needs a value deciding
which pane is visible. Closing compact detail need not discard that selection.
Changing a collection can hide the selected ID; that is a projection, not an
instruction to select a different record.

Layout branches can recreate native view/composable instances. Values kept
inside a conditional pane can disappear with it. Keep selection, drafts,
operations and persisted restoration values in the feature above those branches.
Pass the actual layout mode to slots so affordances follow one decision.

## Example

Open Orbit, star it, then return to the list. Orbit remains selected. Widen the
viewport and its detail can appear beside the list. Switch to Archived and show
a placeholder because Orbit is outside that projection. Return to Starred and
Orbit is available again. No fallback selection or new request is required.
The current breadcrumb follows active browse intent; a retained selection is
not automatically the current route.

## Gotchas and actual use

SplitPane uses local bounds rather than device names. Its host supplies finite
height; each pane scrolls independently. A rail emits supplied destination IDs;
breadcrumbs emit ancestor IDs. Neither constructs routes or owns system Back.
The Workspace app fixture owns these decisions and stores values above the
catalog's theme/family/route changes. Android restores primitives; Swift keeps
current view state, with no new process-restoration promise.

Do not infer a full navigation system from two adjacent panes. Root app routing,
deep links, keyboard/focus restoration, predictive back and fold hinges require
their own native navigation policy. The current tests use synthetic wide native
hosts and do not establish physical tablet or foldable behavior.

Related: [caller-owned components](component-slots-and-caller-owned-state.md),
[selection and undo](selection-identity-and-undo.md),
[Swift substrate](../../frontend/swift/notes/substrate/swiftui-bounded-panes-and-navigation.md),
[Compose substrate](../../frontend/kotlin/notes/substrate/compose-bounded-panes-and-navigation.md).
Next: when a real destination needs deep links, which selection and compact
column values should the native route serialize?
