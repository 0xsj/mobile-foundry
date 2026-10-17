# A durable intent is not a safe resend

Claim: after an external effect may have begun, losing its acknowledgement must
not silently turn the same durable intent into another send.

Origin: queued push-preview slice, 2026-10-17. The existing one-attempt gateway
could return an error after a provider accepted a request. A normal lease-expiry
retry policy would therefore duplicate some alerts. Memory/PostgreSQL fault cases
and a killed helper process exercise the chosen conservative boundary.

## What and why

A lease identifies an executor for a limited time. It cannot make a database and
an external provider atomic. Persist an intent first, claim it, perform preflight
checks, then commit a marker immediately before calling the provider. Reclaiming
an unstarted claim is safe. Reclaiming a sending marker as another send is not.

Conceptual progression:

```text
queued -> leased -> sending -> accepted / invalid_target / uncertain
             |          |
       safe preflight   process/ack loss -> uncertain, no resend
       retry / blocked
```

A fencing token makes late writes from an earlier worker fail. It does not revoke
an HTTP request already in flight. Even a marker commit can be unconfirmed: the
safe response is to avoid the external call unless that commit is confirmed,
possibly recording uncertainty when no effect actually occurred.

## Gotchas

Retrying a preflight failure and retrying a provider timeout are different policies.
Our gateway exposes no universally safe provider-retry classification, so every
post-marker error is conservative. A definitive invalid address may still have
unconfirmed cleanup; resending is not a cleanup retry. A late positive response
can also lose its claim after recovery already recorded uncertainty.

Stable job identity permits enqueue readback/replay without resetting execution.
It does not deduplicate a different new job ID or make APNs/FCM idempotent. Retaining
terminal evidence needs a deliberate capacity/retention policy, not just a queue TTL.

## Used in and related

[Module construction](../modules/internal/notifications/jobs.md) links the state
machine, stores, gateway decorator and checks.
Continue from [snapshot/external-effect reasoning](push-snapshots-and-external-effects.md)
and [provider rejection evidence](provider-errors-and-address-retirement.md).
Next question: which provider failures could carry explicit, trustworthy safe-retry
evidence without disguising an unknown send acknowledgement?
