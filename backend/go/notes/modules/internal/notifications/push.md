# Push preview orchestration

Claim: Notifications checks its exact target and a live Identity answer without
importing Identity aggregates or calling a provider under a database lock.

Origin: backend dispatch slice, 2026-10-17. Evidence and limits are recorded in
[construction](../../../../../../docs/blueprints/push-dispatch.md). Provider
outcomes are injected fakes, not cloud acceptance.

## Read in order

1. [Ports/lease/address](../../../../internal/notifications/app/push.go).
2. [SendPushPreview](../../../../internal/notifications/app/command/send_push_preview.go).
3. [Capability client](../../../../internal/notifications/infra/capabilities/sessions.go).
4. [Memory](../../../../internal/notifications/infra/memory/push.go) and
   [PostgreSQL](../../../../internal/notifications/infra/postgres/push.go).
5. [Optional composition](../../../../internal/notifications/push.go) and
   [conformance/race tests](../../../../tests/push_test.go).

## Control flow and choices

PushLease is a detached ID/principal/session/version reference, not a public
credential. PushTargets returns a secret address only for a current matching
binding. Its separate interface avoids putting sender requirements on public
registry fakes or exposing raw tokens through HTTP.

The handler owns a five-second context and read → session query → read → gateway
flow. It never retries. The consumer-owned SessionEligibility returns bool/error;
its adapter decodes *bool so false differs from missing/null. Dependency failure
stops the command rather than becoming a negative permission. Identity's absolute
session lifetime is distinct from short access-token validity.

The second read protects against changes while waiting on Identity, but cannot
close the final check/send race. Tests rotate/revoke during the foreign call and
rotate/rebind/unregister from the gateway callback, also proving no store lock is
held across these calls.

Definitive rejection calls RetirePushTarget with the original lease. Memory builds
the next aggregate before assignment. PostgreSQL uses the existing guard and
transaction to update token, reservation, disabled state and version together.
Stale results return changed=false. A forced constraint failure preserves token
and reservation; cleanup can subsequently run without invoking a provider.
A lost commit acknowledgement is different from that forced statement rollback:
an error and changed=false mean cleanup is unconfirmed, not that it did not commit.
The handler retains definitive invalid-target evidence alongside a cleanup error;
it does not send again to discover whether cleanup succeeded.

Unknown dispositions and provider errors never erase tokens. Accepted describes
only the provider outcome, is not persisted and does not prove device delivery.
A repeated invocation may duplicate a preview. Formatters hide token/session
even under Go's %#v diagnostic formatting.

NewPushPreview requires a supplied gateway and registered Identity provider after
module assembly. It does not turn the ordinary API into a sender. A future owned
worker can select the adapter without coupling to the HTTP proof workflow.

Next: select a provider, persist attempt/cleanup state and admit native remote
payloads before claiming end-to-end push. Related:
[external effects](../../../patterns/push-snapshots-and-external-effects.md)
and [installation ownership](installations.md).
