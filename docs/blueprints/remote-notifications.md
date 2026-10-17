# Remote notification preview construction

The native catalogs now admit the generic push-preview payload and route taps to
System notifications. Receiving a preview while foregrounded records its ID and
permits native presentation; it does not navigate until a tap. No account data or
session authority comes from the payload. Read [behavior](../../contracts/behavior/remote-notifications.md)
and [receipt/open identity](../../notes/patterns/remote-notification-admission-and-open-identity.md).

## Follow the implementation

1. Start with [Swift application values](../../frontend/swift/packages/FoundryServices/Sources/FoundryServices/RemoteNotificationPreview.swift)
   and [Kotlin application values](../../frontend/kotlin/project/core/services/src/main/kotlin/dev/mobilefoundry/services/RemoteNotificationPreview.kt),
   then their SystemNotifications router files. Receipt and open are separate transitions.
2. Follow [the Apple delegate](../../frontend/swift/apps/FoundryCatalog/Sources/SystemNotifications/AppleSystemNotifications.swift)
   and [Android remote adapter](../../frontend/kotlin/project/app/src/main/java/dev/mobilefoundry/catalog/systemnotifications/AndroidRemoteNotifications.kt).
   Only admitted application values cross the transport boundary.
3. Android's [Firebase service](../../frontend/kotlin/project/app/src/main/java/dev/mobilefoundry/catalog/installations/AndroidPushTokens.kt)
   handles foreground callbacks; [MainActivity](../../frontend/kotlin/project/app/src/main/java/dev/mobilefoundry/catalog/MainActivity.kt)
   handles foreground-slot and SDK background tap Intents. Native shell/catalog owners
   consume only after selecting the diagnostic destination.

## Try it

For live-device testing, complete [token collection](push-tokens.md), explicit
installation registration and [backend preview configuration](push-providers.md).
Allow notifications on the device. Send a preview with its current owning
credential, secure proof and revision. Check foreground receipt, tap the banner,
then repeat from the background and a terminated process. A provider-accepted
request alone is not a display/open test. A foreground receipt diagnostic is
process-local; a cold tap can show its opened ID without a foreground receipt.

Runnable from the repository root, with one iOS simulator booted and an Android
test emulator/device connected:

```sh
swift test --package-path frontend/swift/packages/FoundryServices
make remote-notifications-ios-test
make remote-notifications-android-test
make notes-check starter-check
```

The iOS driver owns a temporary loopback HTTP fixture, injects simulator pushes,
and supplies its address only to the test runner. It never adds a debug receiver
to the application or sends to APNs. Set FOUNDRY_IOS_TEST_UDID to select a simulator
when multiple iOS devices are booted. Its result bundle remains under .cache/ios.
The Android test injects admitted callback data, inspects/sends the actual immutable
PendingIntent, exercises SDK-shaped launcher extras and recreates the Activity.
No cloud project or provider credential is needed for these fixture checks.

## Observed evidence — 2026-10-17

Swift Services executes thirty-two tests, including shared remote admission,
receipt without navigation, tagged consumption/deduplication and bounded eviction.
Kotlin Services' two new remote tests pass, as do the complete Services/app host
tests and Android debug/application-test builds. Nine shared remote fixtures cover
the exact payload and rejected kinds/versions/authority/IDs.

The iPhone 17 Pro/iOS 26.2 simulator passes four selected tests with no skips:
two native envelope/admission cases, a remote simulator-push UI flow proving
foreground receipt plus warm/cold default opens, and the existing local-preview
warm/cold UI regression. The result is retained at
`.cache/ios/remote-preview-20261017-154038.xcresult`. The first run found a
SpringBoard test-helper failure when querying an offscreen notification's
hittability; checking its frame and using a visible coordinate fixed that helper,
and the complete selected suite then passed.

API36_Test/Android 16 passes four instrumented tests with no skips: remote receipt
without navigation until the actual immutable PendingIntent is sent, SDK-shaped
launcher opens, repeated opens/recreation, strict native envelope rejection, and
the two existing local permission/post/clear/admission cases. Results remain in
the app's connected test report and `.cache/remote-preview-android-device.log`.
The temporary read-only emulator was stopped after verification.

Notes and starter inventory checks pass. No Go runtime change or generated-profile
selection was made in this slice; prior Go CLI 0.9.0 readiness remains intact and
native connected starters are not promoted. Live APNs/FCM sending/token issuance,
physical devices, Android killed-process FCM entry, older Android releases,
account-scoped private destinations and durable jobs/retry policy remain separate.
