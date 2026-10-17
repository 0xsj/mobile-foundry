# Android notification permission and PendingIntent ownership

Remote follow-up, 2026-10-17, Firebase Messaging 26.0.0: foreground
notification-plus-data messages reach onMessageReceived, while background taps
reach the launcher Intent with data extras. This follows the
[Firebase receive contract](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
and [SDK notification builder](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-messaging/src/main/java/com/google/firebase/messaging/CommonNotificationBuilder.java).
The app treats SDK metadata as a bounded envelope, not application data, and never
uses a valid Intent as authentication. The native Activity clears accepted extras
before recreation. A synthetic launcher Intent verifies that path without proving
FCM delivery or a killed-process provider callback. See
[construction and exact evidence](../../../../docs/blueprints/remote-notifications.md).

Permission callbacks, notification slots and Activity launch intents have
different owners and lifetimes.

## Origin and evidence

Observed 2026-10-17 with compile/target SDK 36, minSdk 24, AGP 9.0.1 and
API36_Test/Android 16. Native checks execute permission/posting, replacement,
immutable PendingIntent launch, duplicate opens, Activity recreation and owned
clear. They do not establish an Android process-death launch or older-OS behavior.
Read the [app walkthrough](../modules/project/app/README.md#system-notifications--2026-10-17).

## What and why

The screen registers an ActivityResult permission launcher, then waits through a
cancellable deferred bridge only after explicit user action. The callback is a
signal to read current permission/app/channel settings, not a permanent grant.
Lifecycle resume also re-reads settings after an external Settings visit.

A fixed notification tag/ID bounds the preview. An explicit immutable
PendingIntent uses a fixed app URI and one bounded UUID extra; UPDATE_CURRENT
updates that slot instead of accumulating PendingIntents per post. Immutability
prevents sender fill-in, while payload admission still rejects unowned input.
The value has no authority to select an account or read domain data.

## Example

Excerpt from the adapter:

```kotlin
// Excerpt
PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
```

The fixed Intent identity and mutable creator-owned extra are deliberate. An old
rendering can open the current preview, so this is not immutable event history.

## Gotchas and actual use

Handle initial and new intents. Clearing accepted preview fields should preserve
base launch metadata. The native check waits for RESUMED before recreation; after
preserving metadata and synchronizing this transition, that workflow passes.
The earlier failure did not isolate metadata loss from transition timing.

The test executes the notification's real PendingIntent from the app. An attempted
automated drawer gesture could not reliably locate the title and is not claimed
as acceptance. iOS gesture evidence is separate. Cancellation addresses only the
owned preview; the native test leaves another test notification intact.

Related: [Android permission](https://developer.android.com/develop/ui/views/notifications/notification-permission),
[notification creation](https://developer.android.com/develop/ui/views/notifications/build-notification),
[PendingIntent API](https://developer.android.com/reference/android/app/PendingIntent),
and [the shared pattern](../../../../notes/patterns/device-notifications-and-navigation-hints.md).
Next: verify cold process notification opens and any desired remote destination
before extending starter readiness.
