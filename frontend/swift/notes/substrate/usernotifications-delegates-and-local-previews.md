# UserNotifications delegates and local previews

Remote follow-up, 2026-10-17: UNNotificationRequest.trigger is a
[UNPushNotificationTrigger for remote requests](https://developer.apple.com/documentation/usernotifications/unnotificationrequest/trigger).
The delegate now admits that envelope independently from the local slot and uses
the same [foreground/default-action callbacks](https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions).
Transfer only the admitted Sendable value into the main-actor router, never the
native response. Simulator injection can exercise this native path without APNs;
see [construction and exact evidence](../../../../docs/blueprints/remote-notifications.md).

The app must own its notification delegate before launch completes, while a
screen can own the shorter permission/posting interaction.

## Origin and evidence

Observed 2026-10-17 with Xcode/iOS SDK 26.2, Swift 6 mode and iOS 17 deployment
minimum. The iPhone 17 Pro/iOS 26.2 simulator executes local permission/posting
and both warm/cold notification-open flows. This is simulator evidence, not APNs
or physical-device acceptance. Read the [app walkthrough](../modules/apps/FoundryCatalog/README.md#system-notifications--2026-10-17).

## What and why

UNUserNotificationCenter keeps a weak delegate. UIApplicationDelegateAdaptor
retains the app delegate, which installs itself during didFinishLaunching.
Creating a delegate only when a gallery opens would miss earlier responses.
The callback converts the owned request's string payload into a Sendable local
preview before a main-actor Task updates the app's open router.

Permission reads do not prompt. requestAuthorization is an explicit user action;
current settings are read again afterward and on foreground return. A known
local preview can request foreground presentation, but acceptance does not prove
that the OS displayed an alert. Post and clear address only an owned identifier.

## Example

Excerpt from the retained delegate:

```swift
// Excerpt
Task { @MainActor in SystemNotificationComposition.opens.receive(preview) }
```

The admitted value crosses isolation; the native response object does not.

## Gotchas and actual use

The simulator's Notification Center log reported that a collapsed notification
was hinting a side swipe instead of executing the tap. The UI test now opens its
revealed Open action when needed; both cold and warm flows pass. Repeated label
taps alone did not prove an application failure. This behavior is observed for
this simulator/UI state, not a universal iOS gesture requirement.

Related: [Apple scheduling](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app),
[response handling](https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions),
[delegate launch timing](https://developer.apple.com/library/archive/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/SchedulingandHandlingLocalNotifications.html)
and [the shared pattern](../../../../notes/patterns/device-notifications-and-navigation-hints.md).
Next: how should foreground presentation dispatch when more owned categories exist?
