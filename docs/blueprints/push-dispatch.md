# Push dispatch construction

Notifications now has a composed internal SendPushPreview use case, a versioned
Identity session gate, an injected gateway and conditional target retirement.
Memory/PostgreSQL implement matching behavior. The default API does not send push.

Provider follow-up, 2026-10-17: [APNs/FCM adapters and guarded HTTP preview](push-providers.md)
now implement an explicit runtime selection on these ports. The core construction
and original CLI 0.8.0 evidence below remain useful; current standalone output
verification is recorded with that provider follow-up.

Read [behavior](../../contracts/behavior/push-dispatch.md),
[the Identity capability](../../contracts/capabilities/identity-session-eligibility-v1.md),
[snapshot/external-effect reasoning](../../backend/go/notes/patterns/push-snapshots-and-external-effects.md)
and [the module walkthrough](../../backend/go/notes/modules/internal/notifications/push.md).

## Follow the source

Start with [ports/leases](../../backend/go/internal/notifications/app/push.go),
[the handler](../../backend/go/internal/notifications/app/command/send_push_preview.go)
and [optional composition](../../backend/go/internal/notifications/push.go).
The root factory requires a gateway and assembled Identity provider. Default
API composition adds Identity's query capability without selecting a sender.
Explicit PUSH_CONFIG_FILE selection uses the optional provider-aware builders.

The handler reads a target, checks its session, rereads that target and sends
once. Reads verify exact owner/version, enabled state, start/expiry and local
revocation. Invalid-address evidence can retire only the original lease. A newer
binding survives a late provider response. No migration or foreign SQL was needed.

## Run locally

Runnable from the repository root:

```sh
make go-check go-test
make -C backend/go smoke
make starter-check cli-test notes-check
```

Runnable from backend/go with local PostgreSQL tools; the suite creates/stops
only its own disposable database:

```sh
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin python3 -B scripts/postgres_integration.py
```

[push_test.go](../../backend/go/tests/push_test.go) runs dispatch/store cases against
memory and, in that disposable suite, PostgreSQL. Its gateway is a deterministic
fake, not an APNs/FCM client. Checks cover acceptance, definitive rejection/token
erasure/reservation release, uncertain errors, invalid dispositions, negative
session versus outage, missing/stale/foreign/disabled/expired targets, cancellation,
session-check races, send-time rotation/rebind/unregister and concurrent cleanup.
A forced PostgreSQL write failure proves token/reservation/version rollback.

The composed Identity case revokes a real session while deliberately leaving
Notifications' registry enabled; the capability still refuses the next send.
Session conformance covers short access expiry, renewal retaining session ID,
revocation, wrong principal, absent session and absolute expiry. Boundaries refuse
unauthorized callers, malformed requests/answers and missing composition inputs.
Diagnostics hide the provider token and bound session.

## Limits

This initial dispatch batch added no catalog action or public send endpoint. The
linked provider follow-up adds an opt-in manual HTTP route. Native remote
receipt/presentation/opens, durable queue/retry policy and live cloud acceptance
remain separate. This generic preview is not the Files-to-inbox delivery policy
or a new domain event.

The session/target checks reduce stale sends but cannot atomically coordinate an
external effect with revocation/rebinding. Acceptance is not device delivery.
Repeated handler invocation can duplicate a preview; no durable receipt exists.

## Observed evidence — 2026-10-17

Source check/test/smoke and disposable PostgreSQL 14.24 acceptance pass, including
new dispatch/session cases, forced cleanup rollback and existing migration,
atomicity/API SIGKILL/recovery checks. Starter inventory and twenty-eight CLI
tests pass. Generated-copy evidence follows below. Native code
did not change and native suites were not rerun for this batch.

CLI 0.8.0 independently generated
`/private/tmp/mobile-push-starter-lm1anxez/push-api`, module
`github.com/example/push-api/backend`. Its own check/race/smoke and disposable
PostgreSQL 14.24 suite pass, including the new push/session conformance and forced
cleanup rollback. The generated acceptance driver also passes real
auth/account/Notes/Files/inbox/installation proof/rebind/unregister, renewal and
sign-out using the output's Make runner. No checkout import/path remains in build
inputs. These results preserve standalone Go readiness; the dispatch capability
remains a foundation with injected providers, not live cloud acceptance.
