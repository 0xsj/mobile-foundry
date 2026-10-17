# Inbox refresh and setting uncertainty

Claim: a settled server mutation can use authoritative readback for UI recovery
without acquiring the command-receipt semantics of versioned data writes.

Origin: native Notifications slice, 2026-10-17. Source, shared malformed-response
fixtures and real URLSession/OkHttp checks are linked in
[construction and acceptance](../../docs/blueprints/notifications.md). Backend
semantics are in [Notifications behavior](../../contracts/behavior/notifications.md).

## What and why

Mark-read preserves the first server read timestamp. Preference PUT assigns a
boolean, with last committed assignment winning. Neither operation returns a
command receipt or accepts an idempotency key. Repeating a preference assignment
is settled in isolation but can overwrite a newer choice made elsewhere.

The owner keeps the last confirmed display value while sending a change. Before
dispatch it sets needsRefresh. Successful acknowledgement starts a fresh page and
settings read; failure leaves changes disabled and makes refresh explicit. Only a
complete successful refresh clears the requirement. Readback describes current
state; it does not prove which caller committed a value.

Conceptual:

```text
known media=true → send false → acknowledgement lost
  → keep confirmed display, block changes
  → GET current preferences → media=false, controls enabled
```

This differs from Files/Notes uncertain commands, where exact original inputs and
keys must survive for receipt/replay. Reusing that controller here would invent
guarantees that this API does not offer. An optimistic badge decrement is also
unsafe when another client may already have read the same item: refresh the
server's scoped unread count instead.

## Scope and language mechanics

Swift's MainActor owner increments a generation and cancels its Task on bind.
Kotlin's UI-scope owner cancels its Job and increments a generation before exposing
new StateFlow state. Both check cancellation/generation after asynchronous work,
so even a noncooperative old provider cannot publish to a replacement account.
The existing exact-lease AuthenticatedHTTPClient independently gates network work.
Renewal creates a new lease, so the owner reloads instead of retaining stale data.

Swift's Date formatter first admits a real whole-second UTC calendar value, then
adds the admitted microsecond fraction. A round trip rejects normalized impossible
dates; parsing the fraction separately avoids losing it to a millisecond formatter.
Kotlin uses Instant for the same wire timestamps. Neither client orders notices by
time: the server's private commit sequence determines page order, and UTC display
time may move backward with a clock adjustment.

## Actual use and checks

Read [Swift Services](../../frontend/swift/notes/modules/packages/FoundryServices/README.md),
[Kotlin Services](../../frontend/kotlin/notes/modules/project/core/services/README.md),
[Swift catalog](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md) and
[Kotlin catalog](../../frontend/kotlin/notes/modules/project/app/README.md).
[Shared page fixtures](../../contracts/fixtures/notifications/pages.json) cover
scope, duplicate IDs, impossible dates, count bounds, explicit read nullability
and unread-filter consistency. Owner tests simulate committed-but-unacknowledged
settings and late old-account completion. Real HTTP tests drop read/preference
acknowledgements, then fetch current state and check another account's isolation.

Native catalog build/state tests do not establish a generated connected profile,
system push, notification gestures against a real API, durable sync or background
delivery. Keep those acceptance levels distinct in
[starter readiness](../../docs/STARTER-READINESS.md).

Related: [Go notification decisions](../../backend/go/notes/patterns/notification-decisions-and-replay.md).
Next: installation ownership and provider delivery, followed separately by live
inbox hints that trigger authoritative refresh rather than replacing it.
