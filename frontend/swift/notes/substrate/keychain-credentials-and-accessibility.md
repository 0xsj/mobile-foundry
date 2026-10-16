# Keychain credentials and accessibility

An app-owned Keychain item can store a refresh credential while leaving lifecycle and public state in a separate owner.

## Origin and versions

Session foundation observed 2026-10-10, Swift 6.2.3, Xcode 26.2, macOS 26.3,
iOS 26.2 simulator, package minimums iOS 17/macOS 14. Security is linked from
the SDK; the implementation uses generic-password items, not a third-party wrapper.

## What and why

The adapter actor uses a precise class/service/account query and disables
synchronization. Updates replace the data in-place; a missing item is added.
Clear treats a missing item as success. No raw Security status or stored bytes
enter public failure copy. Decode validates a bounded versioned record after
the platform returns bytes. Unknown versions are retained, not silently reset.

AfterFirstUnlockThisDeviceOnly makes data unavailable following restart until
the first unlock, then accessible until the next restart. It prevents migration
to a different device; it is not a biometric prompt or a promise that locking
the device immediately blocks access. [Apple accessibility reference](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly).
The primary DocC JSON for that reference was fetched during this investigation;
its explanation describes accessibility and backup migration, not app lifecycle.

## Example and gotchas

```swift
// Excerpt: attributes owned by KeychainCredentialVault.save.
let attributes: [String: Any] = [
    kSecValueData as String: try credential.encoded(),
    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
]
```

Synchronous Security calls run on the vault actor, separate from MainActor UI.
The actor serializes this instance. Share it through composition when replacing
gallery owners; do not infer namespace-wide locking across separate actors.
Cancellation is checked before native dispatch and does not undo accepted work.

Signing matters to a Keychain test. The first unsigned hosted simulator run
passed 67 existing cases and failed all three new Keychain cases with typed
storage refusals. Repeating with CODE_SIGNING_ALLOWED=YES and CODE_SIGN_IDENTITY=-
passed all 70 hosted cases, including three actual Keychain cases. Local simulator
Make targets now use that ad-hoc signing mode. Distribution settings are unchanged.
No physical reboot/lock, backup restore, biometric or shared access-group
experiment is established by simulator CRUD tests.

## Used in and related

[Native adapter](../../packages/FoundrySession/Sources/FoundrySession/KeychainCredentialVault.swift),
[hosted native checks](../../apps/FoundryCatalog/Tests/SessionKeychainTests.swift),
[Session walkthrough](../modules/packages/FoundrySession/README.md),
[async/actor mechanics](../language/swift-async-ports-and-continuations.md) and
[scope lifetime](../../../../notes/patterns/session-scope-and-credential-lifetime.md).
Next: which accessibility policy matches a real background-sync requirement,
and what on-device experiment demonstrates its locked-device behavior?
