# Installations HTTP v1

All routes require current owned Identity authentication and one X-Installation-Key
header. The key format and ownership rules are in
[registry behavior](../behavior/installations.md). Missing/invalid credentials are
401; valid authentication with malformed input is 400. Wrong installation key or
absent ID is 404. No query strings, Idempotency-Key or If-None-Match are accepted.

| Method | Route | Input | Result |
| --- | --- | --- | --- |
| POST | /v1/installations | installation_id plus registration payload; no If-Match | 201, Location, ETag and sanitized view |
| GET/HEAD | /v1/installations/I | No body or If-Match | 200, ETag and sanitized view |
| PUT | /v1/installations/I | Registration payload; required If-Match | 200, new ETag and sanitized view |
| DELETE | /v1/installations/I | No body; required If-Match | 200, disabled view; settled on already-disabled current binding |

Registration payload: registration_id UUID, provider (apns/fcm), application,
environment and token strings. Update rejects installation_id; the route names
its resource. Shared strict JSON decoding is capped at 16 KiB. The opaque token is
bounded to 4096 bytes and is never echoed. Caller IDs/session/expiry are not inputs.
Unknown fields and malformed/duplicate headers fail through the shared helpers.

The view contains id, registration_id, version, provider, application, environment,
registered, current_binding, created_at, updated_at and expires_at. It contains no
raw token, key/digest or another owner's principal/session identity. Responses
use no-store, safe request metadata/problems and UTF-8 JSON. HEAD admits the same
query and emits no body. Timestamps are UTC microsecond values.

Missing If-Match is 428; malformed preconditions are 400. Stale version/current
registration ID or live duplicate target is 409. Wrong current owning session
cannot unregister (403), even with the installation key and correct version.
Explicit proof-authorized rebinding uses PUT with a new registration ID/current
version. A revoked session cannot register again after its tombstone is applied.

There are no immutable command receipts. On an unconfirmed POST/PUT, inspect GET
through the same installation proof before deciding the next operation. It reports
current state only. Native controllers require this readback and never retry a
write automatically; see [native ownership](../behavior/installations.md#native-registry-ownership).
