# Session scope and credential lifetime

An active account is an admitted, time-bounded capability; a saved credential is only input to restoring that capability.

## Origin and evidence

The session foundation, 2026-10-10, follows local Notes persistence. Source
inspection and deterministic native-language tests establish its lifecycle
ordering. The [behavior contract](../../contracts/behavior/session.md) owns promises;
platform walkthroughs own execution counts and native storage evidence.

## What and why

A principal names a person, an account names data ownership and an authority
names the issuer. Restoration cannot trust stored display/account metadata:
renew through the authority, check the returned scope, save the rotated refresh
credential and only then publish active access. Keep access tokens/passwords
out of persistence and secrets out of UI snapshots and diagnostic descriptions.

A feature can capture a scope lease before work and ask whether it is current
before applying its result. Scope alone is insufficient: sign-out followed by
sign-in to the same account must reject earlier work. An owner discriminator
and increasing epoch prevent that accidental admission across renewals, account
changes and newly constructed controllers. Expiry is checked when using a lease,
independently of any UI timer. The lease is an application admission guard;
server authorization still requires a real validated credential.

## Example

```text
restore → vault.load → authority.renew → scope/expiry admission → vault.save
        → active public identity/account + lease
sign-out → close lease/generation → cancel prior task → wait for prior completion
         → vault.clear → authority.revoke → signed out (possibly with warning)
```

Cancellation cannot undo a native save already underway. Clearing first allows
that save to recreate the credential afterward. Waiting before clearing provides
the local ordering needed for sign-out. A permanently hung authority can delay
cleanup; deadline/abort policies belong to a future connected adapter. Access
stays closed while waiting. A remote grant issued after cancellation may remain
unknown to the owner; local cleanup does not establish server-wide revocation.

## Gotchas and actual use

Transient renewal failure keeps the stored credential while closing access.
Keeping bytes does not guarantee that a server will accept them again: rotation
may have occurred before a save/acknowledgement failed. A refused renewal or
scope mismatch clears them; failed clearing must be reported as incomplete.
Malformed storage is retained until explicit recovery/sign-out.

Shutdown permanently closes an owner while keeping its saved credential. A fresh
owner restores it; delayed callbacks cannot restart the disposed owner. Use one
shared device vault and one active lifecycle writer per namespace. Instance
serialization does not authorize competing controllers or multiple processes.

The catalog demonstrates two fixture accounts and manually advanced time.
It does not authorize guest Notes, implement HTTP replay or authenticate a person.
No biometric, passkey, OAuth, device-lock or physical hardware guarantee follows
from its tests. Account-data migration is separate from credential restoration.

## Related and next questions

Read the [Swift Session walkthrough](../../frontend/swift/notes/modules/packages/FoundrySession/README.md)
and [Kotlin Session walkthrough](../../frontend/kotlin/notes/modules/project/core/session/README.md),
then revisit [local observation lifetime](local-commits-and-observation-lifetime.md).
Next: what should a real authority promise for rotating refresh credentials and
uncertain responses; and which data should be retained or deleted on account change?
