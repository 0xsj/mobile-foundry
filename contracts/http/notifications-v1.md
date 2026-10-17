# Notifications HTTP v1

All routes require Bearer authentication and current owner access through Account.
Responses use existing safe problems/request metadata and no-store headers.
Account and item IDs are canonical nonzero UUIDs. Scope is account plus principal.

| Method | Route | Input | Result |
| --- | --- | --- | --- |
| GET/HEAD | /v1/accounts/A/notifications | limit (1–100, default 20), cursor, unread=true or false | account_id, items, unread_count, next_cursor |
| POST | /v1/accounts/A/notifications/I/read | No body/query | Item with first read_at retained |
| GET/HEAD | /v1/accounts/A/notification-preferences | No body/query | account_id, media_in_app |
| PUT | /v1/accounts/A/notification-preferences | JSON media_in_app boolean, required | account_id, media_in_app |

An item contains id (source event UUID), file_id, type=files.upload_ready,
category=media, occurred_at, created_at and nullable read_at. Timestamps are UTC
with microsecond precision; occurred_at is the producer time, created_at the first
in-app arrival. The body omits internal sequence/principal fields. Empty lists
are arrays; no next page is null. Current scope is repeated as account_id.

Cursors use canonical base64url JSON with version, principal_id, account_id,
before and unread. Clients treat them as opaque. Scope/filter mismatch,
noncanonical encoding, unknown/repeated/empty query parameters, invalid limits
and malformed booleans return 400. These are positions, not signed credentials;
authorization applies on every request. Read routes return 404 for unknown items
within an allowed scope; inaccessible accounts return 403.

Read/preference operations reject Idempotency-Key and If-Match, since they do not
offer receipt/version semantics. Preference JSON follows the shared 16 KiB strict
decoder; missing/null values and unknown fields fail. Read/preference routes reject
queries and unsupported bodies. GET and HEAD use the same admission/query path.

See [behavior and limits](../behavior/notifications.md). There is no public send,
read-all or inbox delete endpoint. Session-bound registration metadata follows
the separate [Installations API](installations-v1.md).
