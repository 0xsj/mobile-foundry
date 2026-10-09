# Compose member slots and selectable links

Claim: a passive identity group and sibling native slots keep role controls
independent while a narrow SelectionContainer enables link text selection.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17, SDK 36/min 24, API 36 emulator. Android's
[text interaction documentation](https://developer.android.com/develop/ui/compose/text/user-interactions)
describes SelectionContainer around text. The
[copy/paste documentation](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)
describes ClipboardManager with ClipData.newPlainText and notes system clipboard
feedback on API 33+. This preview adds no duplicate copy toast.

## Mechanism and example

MemberRow clears semantics only on its passive avatar/identity group and supplies
the full identity description there. Native access/action slots keep their own
labels, roles and disabled states. At font scale at least 1.5 the avatar moves
above identity; logical alignment follows layout direction.

```kotlin
// Conceptual: role choices and command admission belong to the feature.
MemberRow(name, identityCopy, detail = address, avatar = { avatarView() },
    access = { rolePicker() }, actions = {
        ActionButton(requestRemoval, enabled = canRemove) { Text(removeCopy) }
    })
```

ShareLinkCard limits SelectionContainer to the supplied nonnull link Text. Null
uses caller-supplied unavailable copy; actions/status are separate. No LinkAnnotation
or browser destination is inferred. The app's explicit copy callback guards
eligibility and uses ClipboardManager/ClipData for a plain string, then records
the admitted request. A supplied copyText callback provides a test seam.

Membership IDs, role map, invite draft/role/error, revision, link choice and counts
are owned in rememberSaveable above route returns. The tested local string/enum
map restores through the native saved-state registry. It is not durable storage
or an invitation wire format. Removal presence uses transient remember; the
request closes when revision or eligibility changes.

## Evidence, gotchas and actual use

[Native component cases](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SharingComponentTest.kt)
exercise passive identity, independent native actions, unavailable copy and
240-dp font-scale-two RTL growth/target bounds.
[Gallery cases](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SharingCatalogTest.kt)
exercise role menus, invitations, cancel/confirm removal, stale/disabled/pending
admission, saved-state recreation, themes/routes and retained earlier cart state.
The injected copy callback receives exactly the example URL once; ineligible
requests do not invoke it. All four focused checks, both builds and six Kotlin
UI package checks pass. They do not drive the native selection toolbar, OS
clipboard feedback, full TalkBack or real service authorization.

ConfirmationDialog dismisses before invoking confirmation. Capture the rendered
request in a local val for onConfirm: reading the mutable removal state after
dismissal would find null and silently skip the command. Recheck that captured
request against current feature values before applying it. Clear semantics only
for passive identity; wrapping the entire row would hide role/removal controls.

Read [UI walkthrough](../modules/project/core/ui/README.md#sharing-and-access),
[consumer](../modules/project/app/README.md#sharing-gallery) and
[shared membership pattern](../../../../notes/patterns/membership-identity-and-confirmed-revisions.md).
Next: native text-selection/clipboard observations, localized TalkBack and actual
service concurrency admission outside these UI value/slot components.
