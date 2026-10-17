# Installation identity and current-state reconciliation

A device proof should outlive a login, while its authenticated registry client
must be replaced whenever the session lease changes.

## Origin and evidence

The native installation slice on 2026-10-17 follows the backend's proof/CAS
registry. Shared admission fixtures and controller tests run on Swift/Kotlin;
real URLSession/OkHttp acceptance observes dropped create acknowledgement,
explicit session handoff, guarded unregister and sign-out retirement. Native
vault tests separately exercise Keychain and Keystore. The
[construction guide](../../docs/blueprints/installations.md) records checks/limits.

## What and why

There are three secrets with different lifetimes: the login authenticates a
session, installation proof controls one device record, and the provider token
addresses delivery. Putting proof in a user's refresh vault loses the ability
to safely rebind that record after sign-out. Putting a provider token in the
auth slot mistakes an opaque address for a credential.

Use an application/deployment namespace for installation identity. Select secure
storage at app composition, then inject a registry port backed by the current
session lease. Replacing a lease cancels presentation work and reads the binding;
it does not automatically transfer device ownership or replay a token change.

## Example

Conceptual sequence:

```text
load-or-create secure device identity -> GET current binding
explicit target A + new checkpoint -> POST/PUT -> acknowledgement lost
block writes -> GET same device identity -> observe current checkpoint/version
new login -> new exact-lease registry client -> GET -> explicit rebind if desired
```

Unlike an immutable command receipt, a current registration checkpoint cannot
prove a superseded write. Reading revision 4 does not reconstruct revisions 1–3.
Do not label a response failure as a definite rollback; allow a fresh read, then
a deliberate new intent. No raw target needs to survive in a hidden retry queue.

## Gotchas

- Canceling a task does not undo a remote commit. The generation protects local
  state; backend CAS and session retirement protect remote ownership.
- A delayed old-session DELETE must not unlink a newer login. The catalog relies
  on Identity's session-specific revocation fact and never queues that DELETE.
- Local-only sign-out cannot create a server revocation fact. An offline/failing
  revoke leaves retirement unconfirmed; do not promise immediate suppression.
- A disabled/expired registry view says nothing about actual provider delivery.
- Never silently replace an unreadable proof; a live old target may remain
  reserved. Key recovery/rotation and explicit reset require their own policy.

## Used in and related

Read the [Swift Services walkthrough](../../frontend/swift/notes/modules/packages/FoundryServices/README.md#installation-registry--2026-10-17)
and [Kotlin Services walkthrough](../../frontend/kotlin/notes/modules/project/core/services/README.md#installation-registry--2026-10-17),
then each app's vault/composition links. Compare
[session-bound target retirement](../../backend/go/notes/patterns/session-bound-delivery-targets.md)
and [inbox setting uncertainty](inbox-refresh-and-setting-uncertainty.md).

Next question: how should a product own OS token callbacks when no account is
signed in, and how should it retry a desired target without inventing receipts?
