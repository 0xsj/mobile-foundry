# Tabs and feature lifetime

Claim: destination selection, detail navigation and feature lifetime are
separate responsibilities, even when the initial screens are empty.

## Origin and evidence

The 2026-10-08 shell slice adds four placeholders around an existing working
catalog. Source inspection establishes app-owned routes and value-driven
navigation controls. Native builds establish API compatibility; runtime checks
and their limits are recorded in the platform app walkthroughs.

## What and why

Tabs represent peer destinations. Selecting Library from Home changes the
selected destination; it should not append Home and Library indefinitely to a
detail stack. A detail stack represents a path inside a destination. A modal
presentation has a different lifetime again: closing it removes the feature
tree and its native resource adapters.

Keep these identities in app composition. A reusable bar receives items,
selection and a callback. It does not construct screens, services or stores.
Saving a tab identifier can restore selection without promising a durable
feature draft, account session or offline cache.

## Example

```text
App theme + selected tab
  Home       empty
  Library    empty
  Studio     empty → open catalog presentation → detail stack
  Account    empty + app material choice

Close catalog → Studio; detail Back → catalog root.
```

The catalog is presented separately so placeholder tabs stay empty while all
existing examples remain reachable. Future real features can own independent
stacks under their destination; the prototype does not implement those stacks.

## Gotchas

- Keeping every destination composed can keep timers and native GPU loops
  alive while invisible. Saved selection is different from retained work.
- A root Back action must not remove the only stack entry and leave a blank
  navigator. Close the presentation or defer to the platform instead.
- An Activity-wide ViewModel owner can outlive a closed feature. Scope owners
  to the intended presentation and dispose them deliberately.
- Changing an app surface theme does not replace platform-owned navigation
  chrome. Use native tab semantics and document the boundary.
- A glass background needs actual content to sample. A static accent wash is
  enough for a placeholder; it needs no frame clock or GPU renderer.

## Used in and related

See the [Swift catalog walkthrough](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#four-tab-placeholder-shell)
and [Kotlin app walkthrough](../../frontend/kotlin/notes/modules/project/app/README.md#four-tab-placeholder-shell)
for concrete source and execution evidence.
Compare [frame ownership](renderer-frame-ownership.md) and
[material/backdrop ownership](material-themes-and-backdrops.md).
The [component map](../../docs/COMPONENTS.md) distinguishes reserved folders
from implemented controls.
