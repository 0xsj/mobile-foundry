# Provider preview setup

Queue follow-up: [retained preview intents](push-jobs.md) now add separate opt-in
enqueue/status routes, preflight backoff and conservative crash recovery. The
synchronous preview below retains its original one-attempt/no-receipt semantics.

The Go API now has real APNs and FCM adapters and an opt-in generic preview route.
Default builds need no cloud project or keys. Local tests verify the protocols;
no live provider send or device delivery was performed in this batch.

Read [behavior](../../contracts/behavior/push-providers.md),
[adapter walkthrough](../../backend/go/notes/modules/internal/notifications/providers.md)
and [provider evidence reasoning](../../backend/go/notes/patterns/provider-errors-and-address-retirement.md).

## Configure a selected backend

Keep server credentials in an untracked .push-private directory or outside the
checkout. Copy [example.json](../../backend/go/infra/push/example.json) to a private
push.local.json, remove unused routes and replace identifiers/key paths. Credential
paths resolve relative to that file. Route application identifiers must match
the registered native app. APNs sandbox/production must match its signing route;
FCM uses production and the native registry's registration-token mode.

APNs needs its Apple push signing .p8 key, key ID and team ID with topic access.
FCM needs a service-account JSON for the configured Firebase project with send
permission and its Cloud Messaging API enabled. The supplied file must use the
standard Google token URI. Private server credentials never belong in the apps.
No accounts/keys are created, uploaded or provisioned by this repository.

Conceptual local environment configuration (replace the absolute path):

```properties
AUTH_MODE=identity
PUSH_CONFIG_FILE=/absolute/private/path/push.local.json
```

Start the backend with its existing environment workflow. Paths are read once
at startup; restart after rotating configuration/keys. Startup validates all
selected routes and keys without fetching OAuth credentials or sending a push.
With no PUSH_CONFIG_FILE the route remains absent. Invalid selected setup fails
startup. Prefer an absolute config path when using generated root Make runners.

## Request a preview

First sign in, collect a genuine native token and explicitly register it using
[the native token workflow](push-tokens.md). Read the current installation/revision
using its secure proof. The preview route uses that current owning session and
version; it cannot send to a target bound to another session merely because the
caller knows its ID/proof.

Conceptual HTTP request (provide your existing auth/proof/current revision):

```http
POST /v1/installations/INSTALLATION_UUID/push-preview
Authorization: Bearer ACCESS_CREDENTIAL
X-Installation-Key: SECURE_INSTALLATION_PROOF
If-Match: "CURRENT_REVISION"
Content-Type: application/json

{"preview_id":"CANONICAL_NONZERO_UUID"}
```

The result reports accepted or invalid_target, plus whether the original binding
was retired. It does not prove delivery. There is no send receipt or automatic
retry; reusing a preview ID can duplicate a notification. Errors can leave send
or cleanup acknowledgement uncertain. Refresh before another registration change.

The fixed remote payload is separate from local previews. Native catalogs now
admit foreground receipt and route taps into System notifications through the
[remote preview boundary](remote-notifications.md). Background OS behavior still depends on permission,
connectivity and provider policy. Previews expire immediately when not deliverable;
they are not queued for an offline device.

## Verify locally

Runnable from the repository root:

```sh
make go-check go-test
make -C backend/go smoke
make starter-check cli-test notes-check
```

Runnable from backend/go with local PostgreSQL tools; the suite owns a disposable DB:

```sh
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin python3 -B scripts/postgres_integration.py
```

[Protocol tests](../../backend/go/internal/notifications/infra/push/gateway_test.go)
use generated test keys and local TLS/HTTP2 providers. They verify ES256/RS256
signatures, provider requests, caching/concurrent refresh, expiry, rejection
classification, timestamps, bounds, redirects, cancellation and transport-error
redaction. [HTTP checks](../../backend/go/tests/push_http_test.go) use real Identity
credentials and proof/version guards with injected outcomes. They run against
memory and disposable PostgreSQL. CLI tests cover private-file pruning and the
generated ignore/setup files. No real secret fixture is committed.

Primary references: [APNs authentication](https://developer.apple.com/documentation/usernotifications/establishing-a-token-based-connection-to-apns),
[APNs responses](https://developer.apple.com/documentation/usernotifications/handling-notification-responses-from-apns),
[FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api),
[FCM errors](https://firebase.google.com/docs/cloud-messaging/error-codes)
and [Android send fields](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages).

Live APNs/FCM acceptance, signed physical devices, durable
jobs/fanout, retry policy, distributed quotas and connected native CLI selection
remain unverified/separate.

## Observed evidence — 2026-10-17

Source formatting/import policy, architecture/vet/build, full race suite and
compiled process smoke pass. The disposable PostgreSQL 14.24 suite passes the
new HTTP proof/session/version/retirement cases alongside existing migration,
outbox/inbox atomicity, rollback and API SIGKILL recovery. Starter inventory and
all twenty-eight CLI tests pass, including private credential pruning and the
generated ignore/setup files.

CLI 0.9.0 independently generated
`/private/tmp/mobile-provider-verified-9jtwolsv/push-api`, with module
`github.com/example/push-api/backend`. Its own check/race/smoke and disposable
PostgreSQL suite pass. The generated Make runner also passes the external HTTP
acceptance driver for auth/account/Notes/Files/inbox, installation proof/rebind/
unregister, renewal and sign-out. Those acceptance processes clear inherited
PUSH_CONFIG_FILE; they never load a developer's private provider configuration.
The hostile-environment smoke/PG/runner checks also pass with an unreadable host
config path. No source-checkout import or credential is required by the output.

This preserves standalone Go starter readiness; it does not promote connected
native profiles or establish live remote notification delivery. Native code was
unchanged, so native suites were not rerun for this backend slice. The later native
admission follow-up linked above implements versioned receipt/open navigation with
its own simulator/fixture evidence. A signed-device end-to-end flow with chosen
provider keys remains separate.
