# Compose tree actions and indentation

Claim: sibling native action targets keep disclosure, opening and favorites
independent while semantic state and capped logical indentation describe the row.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17, SDK 36/min 24, API 36 emulator. Android's
[semantics documentation](https://developer.android.com/develop/ui/compose/accessibility/semantics)
describes built-in control semantics and stateDescription. TreeRow keeps native
click roles and supplies selection/full narration; TreeDisclosure adds supplied
expanded/collapsed stateDescription to its native IconAction.

## Mechanism and example

The opening Column uses clickable with Role.Button and supplied enabled state.
Its passive artwork/copy descendants clear their semantics; the clickable root
retains its action and complete contentDescription. The disclosure and actions
are siblings, so they remain eligible independently. FileTypeMark is a passive
Text label with supplied narration and no format inference or file access.

```kotlin
// Conceptual: the host supplies flattened entries, branch state and commands.
TreeRow(title, fullLabel, onOpen = open, depth = depth,
    selected = selected, enabled = canOpen, disclosure = branch,
    leading = { FileTypeMark("PNG", "PNG image") },
    actions = { ActionButton(favorite) { Text("Favorite") } })
```

Nonnegative depth times the indentation step is computed as Double before the
maximum clamp. Defaults are 16 dp and a 48-dp cap; finite nonnegative inputs are
required. Logical start padding follows layout direction. Font scale at least
1.5 moves artwork above copy, allowing text to grow vertically instead of clipping.
The consumer computes title-match projection using Locale.ROOT case folding for
its English fixture names, not a general multilingual search index.

## Evidence, gotchas and actual use

[Native component cases](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/FileComponentTest.kt)
exercise independent selected/disabled open/disclosure/action controls, passive
mark semantics and 240-dp font-scale-two RTL bounds at extreme depth.
[Gallery cases](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/FileCatalogTest.kt)
exercise ancestor search, saved expansion/selection/favorites, inspector actions,
state recreation, themes/routes and retained earlier cart state, plus command
rejection and closing an ineligible inspector. All four focused checks pass;
both apps build and six Kotlin UI package checks pass.

Search's clear action is a native TextButton, found by text plus its native click
action in checks. Clearing passive descendants on a clickable root would erase
native meaning, so only supplied artwork/copy is cleared. Feature primitives
use rememberSaveable above route returns; inspector presence uses transient
remember and is discarded during saved-state recreation. LaunchedEffect closes
it when eligibility becomes false without resetting selected identity.

Read [UI walkthrough](../modules/project/core/ui/README.md#files-and-hierarchy),
[consumer](../modules/project/app/README.md#files-gallery) and
[shared projection pattern](../../../../notes/patterns/tree-projection-and-retained-selection.md).
Next: full TalkBack/localized traversal and asynchronous provider child loading;
these native tests establish neither actual file permissions nor filesystem work.
