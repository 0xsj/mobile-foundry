# Native system notification preview

Follow-up: [remote preview admission](remote-notifications.md) now implements the
separate provider payload, receipt/open transitions and tagged router identity.
The permission/local-slot behavior below remains unchanged.

System notification authorization, account inbox preferences, installation proof
and provider tokens are separate capabilities. This slice supplies an explicit
local preview on Swift/Kotlin. It performs no backend call, token registration,
APNs/FCM collection, inbox mutation or account selection.

## Permission and local delivery

Services defines NotificationAccess, LocalNotificationPreview and the replaceable
SystemNotificationPort. The UI-owned SystemNotificationController reads current
permission/delivery settings on entry and foreground return. Reads never prompt.
Only an explicit requestPermission action can show the native permission dialog.
Denial is a notGranted snapshot; an attempted blocked post is an expected failure.
No assertion that another dialog can be displayed follows from notGranted.

Authorization is allowed, quiet or notGranted. Delivery settings are separately
reported because an app/channel/presentation switch can be disabled even with
authorization. canPost admits an allowed/quiet authorization and enabled delivery.
Adapters recheck access before posting. Settings may race that read; permission
failure remains a value. Focus, system suppression and scheduling still apply.

A new explicit preview uses a random canonical UUID and fixed generic title/body.
OS acceptance is not proof of display or an immutable delivery receipt. No account,
principal, server notification text, credentials or installation proof enters it.
iOS submits a three-second local request through UserNotifications. Android posts
immediately through NotificationManager; there is no AlarmManager/background job.

Each platform owns one replaceable preview slot. iOS removes/replaces only its
identifier, with at most one pending and one delivered entry. Android uses a fixed
tag/ID, channel and immutable PendingIntent identity; updating the slot updates
its preview UUID. A tap on an older rendering may therefore open the current
preview. Android applies a five-minute display timeout. Explicit clear addresses
only this slot; it never clears all app notifications. Cancellation may race a tap
or delivery already in progress, and does not retract a received navigation event.

The controller serializes actions, preserves expected failures and reports defects
through the injected hook with generic public failure. Failures disable posting
until access is read again. Native operations are not retried automatically.
Closing cancels/increments the owner generation and refuses late publication;
it does not revoke OS permission or implicitly cancel an intentionally posted
preview. The next screen owner can explicitly clear the same slot.

## Open admission and navigation

The shared payload contains exactly version="1", kind="local_preview" and one
nonzero canonical lowercase UUID preview_id. Unknown/missing fields, bad versions,
other kinds and malformed IDs fail admission. It is a navigation hint with no
authority to authenticate or access account data.

iOS installs a retained UserNotifications delegate before launch completes. It
admits only the owned identifier/category and string payload, presents that preview
in foreground, and routes only the default open action. Other actions/payloads
produce no preview route. The delegate transfers admitted Sendable values to the
main actor, without transferring native response objects or doing network work.

Android uses an explicit immutable Activity PendingIntent. The receiver checks
the action, exact application-owned versioned URI and bounded string-only ID
extra. MainActivity accepts both onCreate and onNewIntent; accepted launch intent
data is cleared before recreation. Unrecognized intents do not gain a route.
The Activity remains exported for normal launcher behavior; preview validation is
not authentication, even for a syntactically valid external intent.

NotificationOpenRouter owns one pending/latest preview and the most recent 32
accepted kind/ID pairs in process memory. A duplicate in that window does not
navigate again. consume must match the complete tagged pending open so an old
completion cannot erase a newer open. It records the last consumed value for the demonstration. There is no durable
inbox, all-history deduplication, cross-process exactly-once or multi-window claim.

App composition holds this router before screens exist. A cold/warm accepted open
presents the catalog and its System notifications route. Navigation consumes only
after adding/selecting that route; an already-visible destination is not duplicated.
The local preview never restores a login, switches accounts or marks an inbox item
read. Future remote notification destinations require their own versioned admission
and current-session/account checks before authoritative data is fetched.

## Scope

Native source is exercised in the catalogs. No connected CLI profile is selected,
and no starter readiness promotion follows from this capability. Real provider
configuration/sending, background registration, account-scoped tap destinations
and physical-device presentation remain separate. Generic remote presentation/open
admission is implemented in the linked follow-up; live provider delivery is not
established by local-preview tests.
