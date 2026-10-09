# SwiftUI account menus and action slots

Claim: native menus can project supplied account selection while independent
profile/session/permission slots keep action policy outside presentation.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3 and iOS 26.2
simulator; package minimum iOS 17/macOS 14. Apple's
[adaptive menu documentation](https://developer.apple.com/documentation/swiftui/populating-swiftui-menus-with-adaptive-controls)
describes Button menu actions and title/subtitle labels, including symbols.
Repository selection, fallback and confirmation guards are local choices.

## Mechanism and example

AccountSwitcher uses Menu and native Button entries keyed by AccountOption IDs.
One optional selected ID supplies the opener value and selected entry. A missing
ID displays the caller's placeholder and never mutates the feature. Disabled
options remain visible; a current option closes the native menu without emitting
a second selection. The current entry includes a decorative check symbol and
the native selected accessibility trait. Menu layout and dismissal are native.

```swift
// Conceptual: a supplied context emits intent to the feature, not a credential.
AccountSwitcher("Switch account", options: choices, selection: contextID,
                placeholder: "Choose an account", enabled: canSwitch) { chooseContext($0) }
```

ProfileHeader, SessionRow and PermissionCard store generic ViewBuilder slot
values. Artwork is hidden from narration because title/detail supply meaning;
status and action slots stay outside that hidden region. ProfileHeader chooses
avatar-above-copy for accessibility Dynamic Type sizes. Text grows vertically;
PermissionCard reuses opaque Card rather than sampling a floating glass surface.

## Evidence, gotchas and actual use

[Native checks](../../apps/FoundryCatalog/Tests/AccountComponentTests.swift) cover
context-qualified removal guards and actual hosted 240-point composition bounds,
larger-text growth, minimum action bounds and logical RTL avatar placement.
They do not establish every native menu gesture or VoiceOver traversal. The real
catalog remains the manual interaction surface.

The consumer reuses confirmationPrompt. Its confirmation callback reads captured
intent before clearing it; the wrapper dismisses presentation before invoking
the callback, so a binding setter must not erase the intent needed by that
callback. Context/disabled changes explicitly discard pending prompts, and
feature admission rechecks captured IDs. Transient prompts remain view-local.

Read [UI source](../modules/packages/FoundryUI/README.md#accounts-and-access),
[consumer flow](../modules/apps/FoundryCatalog/README.md#account-gallery) and
[scope reasoning](../../../../notes/patterns/account-context-and-device-capabilities.md).
Next: test real native permission/foreground adapters and actual account
transitions before claiming authentication, photo access or session revocation.
