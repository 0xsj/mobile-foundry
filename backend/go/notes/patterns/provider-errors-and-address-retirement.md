# Provider errors and address retirement

Claim: a provider failure is evidence about a particular request; only selected
invalid-address evidence justifies erasing that request's original binding.

Origin: APNs/FCM preview adapters, 2026-10-17. Evidence: local TLS protocol cases,
signature checks and real Identity/proof/version HTTP tests with injected
outcomes. No cloud-issued credentials or live device acceptance were used.

## What and why

Provider APIs mix several kinds of refusal: expired credentials, wrong topic or
project, invalid message, throttling, service failure and an address that no
longer exists. Treating every non-success as an invalid token turns a temporary
outage or configuration mistake into destructive registry cleanup.

Own a small mapping per protocol. Require the HTTP status and structured evidence
that actually describes invalidity, then pass that decision to the existing
conditional cleanup. Preserve all ambiguous responses as errors. Unknown fields
may extend an upstream protocol; unknown meanings do not grant deletion authority.

Example (Conceptual):

```text
FCM 404 + generic NOT_FOUND → error; preserve target
FCM 404 + typed UNREGISTERED evidence → retire only original binding revision
APNs 410 + timestamp older than current registration → error; preserve target
timeout after sending → uncertain acknowledgement; preserve target; no replay
```

## Gotchas and use

An APNs unregistration timestamp can predate a new registration of the same
address. Compare it to the captured binding-update time as well as guarding
cleanup with the original version. These checks solve different races: stale
upstream evidence versus a concurrent replacement in your own store.

A connection error may contain a URL with the raw APNs device token. Provider
bodies can also contain private diagnostics. Return owned errors without copying
the native cause/body into logs or UI. Keep credentials, route selection and
endpoint policy inside the adapter rather than passing unknown response text
through a generic application error.

The preview gateway makes one message attempt. OAuth bootstrap is a separate
authorization request. Auth refresh-on-error, transport retries after an uncertain
send and automatic cleanup replay each need explicit policy; none is added here.
The preview UUID correlates requests but supplies no durable idempotency receipt.

This pattern is used by both outbound adapters and the guarded preview route.
Next: persist delivery/cleanup intent separately before adding background retries.
Related: [external effects](push-snapshots-and-external-effects.md),
[protocol mechanics](../substrate/provider-http-and-jwt.md)
and [setup/evidence](../../../../docs/blueprints/push-providers.md).
