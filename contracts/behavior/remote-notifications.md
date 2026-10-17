# Native remote preview receipt and opens

This extends [local previews](system-notifications.md) with the generic payload
sent by [Go provider adapters](push-providers.md). It establishes native receipt,
presentation and app-owned navigation admission, not cloud delivery, account
authorization, an inbox mutation, background work or a durable notification queue.

## Application payload

RemoteNotificationPreview contains exactly string version="1", kind="push_preview"
and preview_id as a canonical lowercase nonzero UUID. Unknown/missing fields,
other kinds/versions and malformed IDs fail admission. Transport adapters unwrap
their envelopes before the same Swift/Kotlin decoder. Arbitrary URLs, account IDs,
server text, credentials and installation proof never enter the admitted value.
Shared positive/negative fixtures cover that application schema.

NotificationOpen distinguishes local and remote cases. The process-owned router
retains one pending/latest open, the last consumed open, the last foreground remote
receipt and the latest 32 distinct accepted opens. Deduplication and consumption
include both kind and preview ID. An old/local completion cannot erase a newer
remote open with the same UUID. Eviction/process death permits repeats; there is
no all-history deduplication, multi-window or exactly-once guarantee.

Receipt only records the admitted remote ID. It does not navigate, authenticate,
restore/switch an account, register a target, fetch data or mark an inbox item read.
Only a tap/default open enters pending navigation. The app presents the catalog,
selects its System notifications route and then consumes that exact open. A
destination already on top is reused. This generic diagnostic destination is
session-independent; future scoped destinations require current authority and
authoritative lookup before showing private data.

## Apple adapter

The retained launch delegate uses UNPushNotificationTrigger to distinguish remote
requests from the owned local slot. The remote envelope has exactly aps plus the
three application fields. aps must be a dictionary and is not projected into
application state; each application value must be a string of at most 128 UTF-8
bytes. Unknown envelope fields are refused. A local request with a remote payload
does not become a push request.

The foreground delegate returns banner/list/sound options only for an admitted
local or remote preview. It transfers the admitted Sendable remote value to the
main actor for receipt diagnostics. Default actions route admitted opens; dismiss
and custom actions do not. Native request/response objects are not transferred to
the main actor, and callbacks perform no backend work. Background OS presentation
is outside this admission gate; an unrecognized alert may still be displayed by
the OS but cannot gain the app's preview route.

## Android adapter

PushMessagingService accepts the notification-plus-data shape in onMessageReceived.
Data-only messages are ignored. The strict data decoder runs before receipt or
presentation. Foreground callbacks record the admitted value on the UI dispatcher
and post fixed generic content when permission/channel settings allow it. One
immutable Activity PendingIntent/remote notification slot is replaceable; local
previews have a separate slot. Posting never prompts or retries and OS acceptance
does not establish display. The manifest supplies a remote channel and small icon
for SDK-owned background presentation; the Activity creates that channel.

An app-posted remote PendingIntent requires its exact action/versioned URI and
the three string fields. SDK background notification taps use the explicit launcher
Activity, ACTION_MAIN, no URI and no categories other than LAUNCHER. Their extras
contain the same strict application fields plus a bounded allowlist of SDK
metadata. Known message/sender/priority strings, numeric sent-time/TTL and a
bounded string-only Analytics sub-bundle are ignored rather than passed to core.
Unknown extras, wrong types, malformed parcels, more than twenty keys and long
values are refused. The allowlist follows the pinned Firebase Messaging 26.0.0
behavior; new SDK envelopes need explicit review rather than arbitrary projection.

MainActivity admits onCreate and onNewIntent, then replaces an accepted launch
Intent with a clean explicit launcher Intent before recreation. An exported
Activity and a valid URI/payload are not proof that Firebase delivered the hint.
No privileged behavior depends on the transport claim. SDK-owned background
notifications may display content before application admission and are not
cleared by the local-preview clear action.

## Verification boundary

Native host fixtures and adapter checks establish admission/state behavior.
Simulator-injected Apple pushes exercise native foreground delivery/default opens
without APNs. Android fixtures exercise the callback admission, actual OS
PendingIntent and SDK-shaped launcher extras without contacting FCM. These are
distinct from signed-device provider delivery and Android cold-process FCM
acceptance. Connected generated native profiles remain unselected.
