# Optional APNs and FCM preview providers

Follow-up: [queued generic previews](push-jobs.md) provide a separately selected
enqueue/status workflow using this gateway. The synchronous endpoint below still
has no job receipt and repeated synchronous requests may send again.

The Go API can explicitly select real provider HTTP adapters behind the existing
[dispatch gateway](push-dispatch.md). Default startup selects no provider and
exposes no send endpoint. This is a manual generic preview, not automatic business
notification delivery, a queue, a receipt or live-cloud acceptance.

## Selection and ownership

PUSH_CONFIG_FILE selects a bounded private JSON configuration and requires
AUTH_MODE=identity. Notifications' root facade loads its owned infra/push adapter;
cmd never imports adapter internals. Memory/PostgreSQL builders declare the extra
Identity session capability when a gateway is selected. Missing capability or
invalid configuration refuses startup rather than falling back to a fixture.

Configuration contains one to sixteen unique exact provider/application/environment
routes. Credentials are server-owned files, resolved relative to that configuration.
Only known fields, supported routes, key types and credential shapes are admitted.
APNs uses a P-256 PKCS8 signing key and ten-character team/key identifiers. FCM uses
a matching project/service-account RSA PKCS8 key of at least 2048 bits. Only the
standard Google OAuth endpoint/universe is supported. There is no ADC, key reload,
arbitrary endpoint, certificate authentication or alternate cloud selection.

Loading makes no network request. Provider hosts are fixed in source; production
clients require TLS and refuse redirects. Shared idle connections close after API
shutdown. Files/responses are bounded. Tokens, private keys, raw provider bodies
and transport URL errors never enter application diagnostics or public responses.

## Wire behavior

APNs sends HTTP/2 POST to the selected sandbox/production host and /3/device/token.
Token encoding is canonical lowercase hex with no fixed byte-count assumption.
Headers select the exact topic, alert push type, priority 10, expiration 0 and caller
preview UUID as apns-id. ES256 authorization uses raw 64-byte R/S JWT signatures,
cached for up to fifty minutes while the injected clock has not moved backward.

FCM exchanges a signed RS256 service-account assertion for a scoped bearer token
using the fixed Google OAuth endpoint. The cache coalesces concurrent refreshes
and waits cancellably. Credentials refresh before expiry. HTTP v1 sends only to
the configured project, using message.token, the application as restricted package
name and Android TTL 0s. This is the registration-token protocol used by native
registry v1, not Firebase installation-ID registration mode.

The payload is fixed generic title/body plus string version=1, kind=push_preview
and preview_id. APNs carries these beside aps; FCM carries them in data with a
notification alert. There is no arbitrary text, account selection, inbox content
or domain command. Expiration/TTL zero deliberately avoids queueing offline previews.

Only one message POST is attempted. OAuth acquisition is a separate authorization
request, not a message retry. No retry occurs on expired credentials, throttling,
connection failure or unknown acknowledgement. Positive acceptance proves no
display, receipt or native open.

## Evidence for invalid-address cleanup

APNs HTTP 400/BadDeviceToken is definitive. HTTP 410/Unregistered is definitive
only with a positive millisecond timestamp at or after this binding's RegisteredAt
and no more than five minutes ahead of the local clock. RegisteredAt is the last
binding/token update. Missing, old or implausibly future timestamps preserve the
target. Topic mismatch, credentials, throttling, malformed and server failures
are errors and preserve registration.

FCM cleanup requires HTTP 404, error.code=404, status=NOT_FOUND and a typed
google.firebase.fcm.v1.FcmError detail with UNREGISTERED. Generic 404, INVALID_ARGUMENT,
permissions/project mismatch, malformed responses and transient failures are not
invalid-token evidence. HTTP 200 requires a nonempty message name for the selected
project. Errors are sanitized and retain no response body/cause containing tokens.

The existing command retires only the original owner/version. Timestamp admission
does not remove the remaining authority/check/send race or make provider effects
atomic with a database. A cleanup error may hide a committed transaction; read
registry state before another write and never resend simply to retry cleanup.

## HTTP preview

Only selected runtimes register POST /v1/installations/{id}/push-preview. It requires
a current Identity access credential, exactly one X-Installation-Key, If-Match,
and a strict JSON object containing preview_id as a canonical nonzero UUID.
Principal/session come only from credential resolution. Proof, current owning
session and exact revision are checked before the dispatch gate. Queries,
Idempotency-Key, If-None-Match, unknown fields and other methods are refused.

HTTP 200 contains exactly preview_id, disposition (accepted or invalid_target)
and retired. It carries no token/provider credential, registry ETag or device
delivery promise. Responses are no-store. Provider failures use ordinary owned
failure responses; an uncertain send/cleanup may have taken effect despite an
error. Refresh registration before subsequent changes. Repeating a preview UUID
may send again; it is not an idempotency key. No per-account audience, distributed
rate budget or durable attempt/result retention is established by this endpoint.

## Verification boundary

Local TLS protocol tests verify signatures, headers, payloads, cache behavior,
outcomes, redirects and cancellation without contacting providers. HTTP checks
use real Identity/proof/CAS admission with an injected gateway, under memory and
disposable PostgreSQL. Cloud-issued credentials/tokens, physical-device delivery,
generated connected native profiles remain separate acceptance. Native catalogs
now admit this generic payload through the [remote preview boundary](remote-notifications.md)
and route taps to notification diagnostics; simulator/fixture opens do not establish
live provider delivery. See [setup and evidence](../../docs/blueprints/push-providers.md).
