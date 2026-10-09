# Compose account menus and action slots

Claim: native menu visibility is temporary presentation state; selected account,
capability status and guarded actions remain supplied feature values.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17; compile/target SDK 36, min SDK 24. Google's
[menu guide](https://developer.android.com/develop/ui/compose/components/menu)
documents expanded/onDismissRequest and native menu entries. Its
[semantics guide](https://developer.android.com/develop/ui/compose/accessibility/semantics)
distinguishes state descriptions, child merging and explicit semantics changes.
Account scope and confirmation policy here are repository choices.

## Mechanism and example

AccountSwitcher uses a native ActionButton and DropdownMenu with selected entry
semantics and a decorative check. An unknown supplied ID displays the placeholder
without selecting a fallback. Current/disabled choices emit no intent. Visibility
uses remember keyed by enabled/empty state; changing either dismisses the menu
and does not reopen it when availability returns. Visibility is never saveable.
The small option list is eager, suitable for a few supplied identities.

```kotlin
// Conceptual: selected context stays in the owner; only menu visibility is local.
AccountSwitcher("Switch account", choices, contextID, "Choose an account",
    onSelect = chooseContext, enabled = canSwitch)
```

ProfileHeader uses LocalDensity.fontScale >= 1.5 for avatar-above-copy. SessionRow
reuses ListRow and supplied activity text. PermissionCard reuses opaque Card.
Artwork containers clear descendant semantics; heading, status and actions stay
outside those decorative containers. Native buttons retain independent focus.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/AccountComponentTest.kt)
exercise native current/disabled options, controlled selection, unknown IDs,
menu dismissal, 240-dp font-scale-two growth, RTL and independent action targets.
The first menu test matched both the opener and its current Personal option;
matching the entry's Selected semantics removes that ambiguous harness lookup.
These checks are not a full TalkBack/localization/device audit.

The app keeps account IDs, qualified removed IDs, permission scenario and counts
in rememberSaveable above preview branches. Pending confirmations use remember
keyed by context/availability; saved-state recreation discards them. The dialog
confirm lambda captures an intent value before onDismissRequest clears local
presentation, then the feature guards context and eligibility again. App outline
icons are local vector resources; no icon-library dependency was added after
the first consumer compile exposed unavailable material-icons imports.

Read [UI source](../modules/project/core/ui/README.md#accounts-and-access),
[consumer checks](../modules/project/app/README.md#account-gallery) and
[scope reasoning](../../../../notes/patterns/account-context-and-device-capabilities.md).
Next: integrate OS permission refresh and guarded real session commands outside
these UI patterns, with explicit principal/workspace scope and credential ownership.
