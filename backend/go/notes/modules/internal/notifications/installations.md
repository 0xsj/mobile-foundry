# Installation ownership walkthrough

Claim: a delivery address needs both physical-installation proof and an admitted
session binding, with versioned handoff and event-driven retirement.

Origin: backend installation slice, 2026-10-17. Source and memory/PostgreSQL/process
execution follow Bento's owned ports and receiving-adapter convention. Read
[the contract](../../../../../../contracts/behavior/installations.md) for guarantees.

## Source reading order

1. [Private aggregate](../../../../internal/notifications/domain/installation.go):
   route/token admission, snapshots, Bind and Disable. Disabled state contains no
   routing token. Snapshot/spec formatting is redacted.
2. [Ports and use-case conditions](../../../../internal/notifications/app/installations.go),
   [Register](../../../../internal/notifications/app/command/register_installation.go),
   [Unregister](../../../../internal/notifications/app/command/unregister_installation.go)
   and [Get](../../../../internal/notifications/app/query/get_installation.go).
3. [Proof adapter](../../../../internal/notifications/infra/security/installation_proofs.go)
   and [private fingerprints](../../../../internal/notifications/infra/records/installations.go).
   Raw installation proof is never stored; routing token and proof have different
   jobs. The target hash includes provider/application/environment.
4. [Memory](../../../../internal/notifications/infra/memory/installations.go),
   [PostgreSQL](../../../../internal/notifications/infra/postgres/installations.go)
   and [0002 migration](../../../../migrations/notifications/0002_installations.sql).
   One owned guard covers proof/CAS/unique-target decisions; it is a documented
   scaling tradeoff, not an excuse for a cross-module transaction.
5. [Receiving adapter](../../../../internal/notifications/infra/events/identity.go),
   [RetireSession](../../../../internal/notifications/app/command/retire_session.go),
   [HTTP](../../../../internal/notifications/transport/http/v1/installations.go) and
   [composition](../../../../internal/notifications/module.go).

## What and why

A UUID names an installation but does not prove control of it. An opaque provider
token is a delivery address and can rotate. The installation key proves binding
control; the access credential admits a current Identity session. Combining these
allows an explicit same-device user switch without permitting guessed IDs to
claim targets. Current owning-session checks and CAS keep an old cleanup from
removing a later binding, including same-session token rotation.

Identity exposes session evidence through v2 while preserving v1's exact response.
The generic Principal holds trusted optional session metadata; development fixtures
remain sessionless. Notifications never accepts actor/session/expiry from JSON and
never reads Identity SQL. See [session capabilities](../identity/README.md).

Conceptual:

```text
register A at v1 → bind B with proof + If-Match v1 → v2
late cleanup by A → owning-session refusal
late revocation of A → tombstone A; current binding B survives
late in-flight register A → tombstone refusal
```

Checking only current rows misses the last case when revocation arrives before
registration. The retained session tombstone closes it. Cleanup, tombstone and
source marker commit together; receiver failures roll all three back. Retrying the
same event is harmless, but changing its admitted input under the same ID fails.

## Gotchas, actual use and evidence

registration_id is a current checkpoint, not durable receipt history. Never reuse
one for a new intent. The registry does not solve physical device attestation,
lost-key recovery, offline sign-out or provider acceptance. registered plus a
captured expiry is metadata, not a current eligibility decision. Future sending
needs fresh session/binding validation and explicit external-send race semantics.
The raw routing token is private plaintext metadata until erased; proof is hashed.

Used by the Go API and full Go CLI selection. Read
[conformance/privacy/HTTP checks](../../../../tests/installations_test.go) and
[process recovery](../../../../scripts/postgres_integration.py).
Source formatting/architecture/vet/build/race and disposable PostgreSQL 14.24 checks
pass, including blocked revocation recovery after SIGKILL and persisted token
erasure. Independent extraction evidence belongs in
[starter readiness](../../../../../../docs/STARTER-READINESS.md).

Related: [notification decisions](../../../patterns/notification-decisions-and-replay.md)
and [session-bound target retirement](../../../patterns/session-bound-delivery-targets.md).
Next: native vault/token ownership before selecting a real outbound provider.
