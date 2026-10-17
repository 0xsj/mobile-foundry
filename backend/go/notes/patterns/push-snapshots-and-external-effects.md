# Push snapshots and external effects

Claim: rechecking an exact binding reduces stale sends, while versioned cleanup
prevents an old provider response from damaging new state.

Origin: backend dispatch foundation, 2026-10-17. Evidence: matching memory/
PostgreSQL race cases, Identity capability revocation checks and forced cleanup
rollback. Provider outcomes are injected; no cloud send or exactly-once delivery
is established.

## What and why

A provider call has a different lifetime from a transaction. During its network
wait a user can sign out, rotate a token or rebind the installation. Holding a
database lock delays these changes without making the external effect reversible.

Capture a versioned binding reference, read its address, ask Identity about the
bound session and recheck the binding after that answer. Release store resources
before sending. Condition any invalid-address cleanup on the original version
and owner.

Example (Conceptual):

```text
read revision 7 / session A → provider request starts
installation rebinds to revision 8 / session B
provider rejects revision 7's address
retire-if-current(revision 7, session A) → no-op; revision 8 survives
```

## Gotchas and actual use

The session answer is point-in-time. It blocks a committed revocation before
asynchronous cleanup arrives, but revocation can still happen between query and
send. Repeating checks cannot eliminate that race across separately owned stores
and a provider.

Short access-token expiry differs from absolute session expiry. A background
binding should not be disabled just because its access credential needs renewal.
Conversely, an enabled registry record cannot prove its session is live. An
authority outage is an error that stops the attempt, not an ordinary negative
answer or permission to proceed.

Invalid-address evidence differs from timeouts, auth/config failures and throttling.
Erasing on every provider error destroys valid state. Even definitive rejection
must not erase a replacement. Commit erasure/reservation/version atomically and
guard it with the observed owner/version.

These mechanics serve the internal preview handler. A durable sender needs owned
attempt/result/cleanup state: acceptance can be lost before acknowledgement and
cleanup can fail after rejection. A lost cleanup commit acknowledgement may hide
a successful transaction, so an error is not proof of unchanged state. Retrying
guarded cleanup must not require another send.
Next: define queued intent, provider evidence, bounded retry and retention.

Related: [session-bound targets](session-bound-delivery-targets.md),
[module composition](ports-and-module-composition.md),
[native address intents](../../../../notes/patterns/provider-addresses-and-registration-intents.md)
and [construction/evidence](../../../../docs/blueprints/push-dispatch.md).
