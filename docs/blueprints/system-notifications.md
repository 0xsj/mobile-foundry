# System notification preview construction

Both catalogs expose System notifications: native permission/settings controls,
a real local preview and an admitted navigation route for preview opens. This
uses UserNotifications on iOS and NotificationManager on Android. It does not
use APNs, FCM, a cloud project or the Go backend.

Read [the behavior contract](../../contracts/behavior/system-notifications.md),
[navigation-hint reasoning](../../notes/patterns/device-notifications-and-navigation-hints.md)
and the native Services/app walkthroughs.

## Try it

Open Studio → Open catalog → System notifications. Reading settings does not
prompt; choose Allow notifications deliberately. If permission was denied or
the notification channel/app switch is off, open notification settings and return,
then refresh. Posting stays disabled until current settings allow it.

Choose Show test notification. iOS schedules it three seconds later; Android
posts immediately. Leave the app or open Notification Center/the drawer, then
open the generic preview. The app presents the System notifications route and
shows the admitted UUID. Clear test notification cancels only the owned preview
slot. Device Focus/Do Not Disturb and quiet/channel settings can affect display.

Each post replaces a bounded slot. The content contains no account data. The
installation registry card remains separate and still accepts explicit probe
addresses; device permission does not invent or register an APNs/FCM token.

Follow-up: [optional provider-token adapters](push-tokens.md) now offer explicit
collection in a separate card. Default builds report missing provider setup.
Live issuance, remote opens and cloud sending remain separate acceptance.

## Ownership

Services owns the permission snapshot, typed local payload, replaceable OS port,
single-action controller and bounded open router. App composition selects native
adapters and holds pending opens before navigation exists. Shared UI components
only render state and actions. The native delegate/Activity receives untrusted
input, admits its owned preview and transfers typed values to the UI owner.

The router keeps one pending/latest open and a bounded in-process duplicate window.
Navigation consumes by exact ID. Repeated callbacks do not duplicate a visible
route, and an old consume cannot clear a newer open. There is no durable inbox,
all-history deduplication or business authority in this payload. No login is
restored, account switched or inbox item marked read as a side effect.

iOS retains its center delegate through UIApplicationDelegateAdaptor and installs
it before launch completes. It presents known previews in foreground and handles
only the default open action. Android uses an explicit immutable PendingIntent,
one channel/tag/ID and a versioned application URI. MainActivity handles both
initial launch and onNewIntent, then clears accepted intent data before recreation.

Permission requests are screen-owned. Closing cancels UI work and drops late
publication without undoing an OS post or dismissing native permission policy.
An explicit clear remains available to later owners. OS acceptance is not proof
of display; failure does not trigger an automatic retry.

## Checks

Runnable from the repository root with the native simulator/emulator available:

```sh
make http-test
make system-notifications-ui-test
make starter-check notes-check
```

Shared fixture/owner checks cover strict version/kind/UUID admission, explicit
permission actions, changed permission at post time, no automatic retry, current
open consumption and ignored late native responses after close. Native verification is recorded below. A host fake does not establish system gestures.

Provider collection/sending, remote account destinations, physical-device behavior,
older OS versions and independently generated connected profiles remain separate.

Primary references: [Apple local notifications](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app),
[Apple response handling](https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions),
[Android permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
and [Android creation](https://developer.android.com/develop/ui/views/notifications/build-notification).

## Observed native evidence — 2026-10-17

Both catalogs build. Four Swift host cases and three Kotlin host cases pass,
including the shared twelve-case payload corpus and owner/open behavior.
The ordinary HTTP/Services regression suites pass; their environment-gated Go
acceptance cases are not selected by this local-notification batch.

The iPhone 17 Pro/iOS 26.2 Xcode result
`.cache/ios/Logs/Test/Test-FoundryCatalog-2026.10.17_14-10-55-+0300.xcresult`
reports two passed, zero failed/skipped: owned native request admission and a real
local-notification UI flow with permission, warm open, terminated-app cold open
and clear. Notification Center requested a side swipe/Open action in this UI
state; the test handles that observed system interaction.

API36_Test/Android 16 executes two cases with zero failures/errors/skips. They
exercise native permission, NotificationManager posting/replacement, immutable
PendingIntent execution into MainActivity, duplicate opens, resumed Activity
recreation, owned clear that preserves an unrelated test notification, and
invalid Intent admission. The test executes the notification's real content
PendingIntent; an automated notification-drawer gesture was unreliable and is
not claimed as gesture acceptance. Android cold process launches are not verified.
Preserving base Intent metadata and waiting for RESUMED fixes the recreation
workflow; the earlier failure did not isolate those two causes. The two existing
Android shell saved-state/back/close regression cases also pass.

No provider credentials, entitlement changes or SDK dependencies were added.
Remote notification receipt/presentation, account-scoped payloads, real push,
locked physical devices, older OS versions and generated connected combinations
remain unverified. Local native/Go starter readiness is unchanged.
