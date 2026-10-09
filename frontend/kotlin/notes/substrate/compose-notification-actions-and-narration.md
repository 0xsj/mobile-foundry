# Compose notification actions and narration

Claim: retaining native clickable semantics while replacing only passive child
copy gives a notification one open action and separate caller-owned controls.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17; compile/target SDK 36, min SDK 24. Google's
[merging/clearing guide](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing)
describes clickable merging, independently interactive children and the effects
of clearing semantics. Which meaning to supply is feature policy.

## Mechanism and example

NotificationRow uses a native clickable Row with Role.Button, enabled admission
and a supplied contentDescription. Only passive artwork/copy descendants clear
their semantics. The clickable root keeps its native action and disabled state.
Sibling action slots lie outside it, with their own eligibility and targets.
CountBadge replaces passive Text semantics with complete caller narration rather
than trying to interpret or cap its formatted label.

```kotlin
// Conceptual: copy, state and commands are supplied by the host.
NotificationRow(title, message, timeCopy, readCopy, unread, completeCopy, open,
    actions = { ActionButton(markRead, enabled = canMarkRead) { Text("Mark read") } })
```

The weighted copy column grows vertically beside passive artwork. Logical Row
placement follows layout direction. The host hoists nonsecret filter/read/archive/
undo/opened primitives above route branches; the new preview branch comes after
earlier family owners so opening it does not remove their remembered values.
Sheet presence uses remember, not rememberSaveable. LaunchedEffect dismisses
presentation when active-identity eligibility becomes false, without resetting
the newly opened flag merely because eligibility changed from false to true.

## Evidence, gotchas and actual use

[Component cases](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/NotificationComponentTest.kt)
check supplied narration, native open role/disabled state, independent actions,
passive artwork and 240-dp font-scale-two/RTL growth with 48-dp action bounds.
[Consumer cases](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/NotificationCatalogTest.kt)
check filtered read-on-open, archive scope/undo, unavailable detail dismissal,
theme/family/route changes, saved-state recreation and retained earlier cart state.
Four focused cases pass on the isolated API 36 emulator; six UI package cases pass.

Clearing a clickable root can erase native meaning. Clear only passive descendants
whose full meaning is supplied on their parent; decorative slots cannot contain
unique information or interactive controls. Native menu items have their own
scroll surface. Disabled UI is not service authorization. No TalkBack traversal,
all-locales or physical-device performance audit is established.
Read [UI walkthrough](../modules/project/core/ui/README.md#notifications-and-inbox),
[consumer flow](../modules/project/app/README.md#notifications-gallery) and
[shared identity pattern](../../../../notes/patterns/inbox-projection-and-read-identity.md).
Next: test assistive traversal and scoped real read/archive commands through a
service adapter, including concurrent server changes and offline receipt policy.
