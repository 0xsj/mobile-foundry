# Account notifications

Implemented backend slice, 2026-10-17. Notifications owns an account/principal
inbox, first-read acknowledgements and a media in-app preference. Domain state is
private; application ports select memory or PostgreSQL. No Files/Account store,
domain type or SQL crosses into its core.

## Curated delivery

| Source | Type | Category | Recipient | Channel | Default |
| --- | --- | --- | --- | --- | --- |
| files.upload_completed.v1 | files.upload_ready | media | Authenticated actor in the source fact | in-app | enabled |

This intentionally tells the uploader that their upload committed. It does not
infer team membership, resolve a current owner or broadcast to every account user.
The current Files policy is owner-only. New audience rules need explicit facts or
consumer-owned directory capabilities. This is a curated mapping, not a public
arbitrary-send endpoint. No filename, content, digest, URL, credential or profile
copy enters Notifications. Clients render the type and may resolve file_id through
Files with current authorization; old notices may point to deleted files.

The receiving adapter strictly admits source name, producer, IDs, account and
version 2, then calls its own DeliverUpload handler. Producer metadata/outbox and
notification item/inbox are separate transactions. Delivery is at-least-once;
consumer notifications.files commits a fingerprinted marker with its decision.
Same source ID/input is a no-op, including when muted. Same ID/different admitted
input is a conflict. Recorded time and preferences are excluded from the source
fingerprint. Request/correlation envelope metadata is not retained here.

Preference decision, sequence allocation, item insertion and inbox marker commit
atomically. Muting prevents future first deliveries, leaves old items intact and
never resurrects a suppressed event after unmuting. A per-scope PostgreSQL row lock
serializes delivery, preference updates, read changes and page snapshots. No
foreign/provider call runs under that guard. Preferences are evaluated at delivery,
so a previously unconsumed backlog uses the current preference. There is no age
cutoff or automatic retention in this slice.

## Owned reads and settings

Every public use case authenticates and checks account.access.v1 through a
consumer-owned AccountAccess port. Dependency failure remains failure. Listings,
counts, preferences and read changes stay bound to account plus principal. There
is no principal-global active account. Current policy admits only the owner.

Items list newest arrival first using a commit-ordered per-scope sequence, not
source timestamps. A page's count and rows share a guarded snapshot; pages over
time are not a frozen snapshot. The optional unread filter and cursor are bound
to account/principal/filter. New arrivals appear on refresh; marking items read
may remove them from later unread pages. The count always covers all unread items
in the scope, independent of pagination/filter.

Mark-read keeps the first read_at even on a repeat with a later clock. Preference
PUT assigns a complete boolean value, with last committed assignment winning.
These settled operations use no command receipt/CAS; unsupported mutation
Idempotency-Key/If-Match headers are rejected. A preference retry may override a
concurrent setting change; this is not an offline causal command protocol.

## Providers and limits

Memory uses one mutex, bounded to 10,000 source decisions and 10,000 explicit
disabled scopes. It loses everything on exit. PostgreSQL owns notifications schema,
an explicit immutable migration and its own pool. API startup verifies migrations;
only cmd/migrate applies them. NOTIFICATIONS_DATABASE_URL inherits DATABASE_URL.
Workers belong to existing producer outboxes; Notifications starts no extra timer.

Native connected inbox screens now consume these routes through account-bound
Swift/Kotlin repositories and exact session leases. The backend
[installation registry](installations.md) now owns session-bound target metadata.
Native token collection, APNs/FCM/email senders, outbound delivery leases,
read-all/delete, Realtime hints, retention, erasure and operational replay remain
separate slices. No provider send port is claimed as implemented by an unused stub.

## Native inbox consumers

NotificationInboxReader and NotificationInboxWriter are separate native ports;
NotificationInboxRepository composes them. HTTP adapters accept an injected client,
bind account/principal IDs and perform one request per operation. They never renew,
retry or replay automatically. Catalog composition injects AuthenticatedHTTPClient
for the exact current lease and invalidates the owner on every lease change,
including renewal. Closing/signing out clears rows, count, settings and details.

Adapters admit the known type/category, UUIDs, real UTC calendar timestamps,
explicit nullable read_at, nonnegative safe-integer count, distinct bounded rows,
unread-filter consistency, current account and bounded progressing cursors. Sequence
is not present in item DTOs, so clients retain server order and do not sort by
created_at. Mark-read must acknowledge the original item/file/times and retain a
known read timestamp. A setting response must echo the requested boolean. Public
failure metadata is preserved; cancellation still uses native control flow.

The app-owned owners load pages of 20, retain at most 200 items and expose manual
refresh, unread filtering, pagination and media preference. A full refresh replaces
rows and reloads preferences. Duplicate rows across pages stop pagination and ask
for refresh. New arrivals are not polled or pushed in this slice. Inbox count comes
from the server, rather than decrementing a potentially stale local badge.

Read/settings controls wait for acknowledgement and a fresh read. An unconfirmed
write sets needsRefresh and blocks further changes until a full successful refresh.
There is no retained command, background queue, receipt lookup or automatic replay.
In particular, blindly retrying a preference assignment could overwrite a newer
choice. Refresh failure keeps the recovery requirement. Session replacement
discards stale completions and reloads current server state through the new lease.

Reusable NotificationRow/CountBadge/ToggleField stay passive. The connected card in
Go identity and accounts renders upload-ready copy and a detail sheet containing
the file reference; it does not resolve foreign Files state or offer unsupported
mark-unread, archive, bulk-read or push-permission controls. The separate component
preview remains an explicit fixture demonstration. A connected catalog does not
establish a generated connected native starter profile.
