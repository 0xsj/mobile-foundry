# Provider addresses and registration intents

Claim: a provider callback changes an address candidate; a separate authenticated
intent decides which backend session owns that address.

Origin: native APNs/FCM collection, 2026-10-17. Evidence: matching Services owner
tests and native callback/request-race tests. These establish local ordering;
they do not prove a cloud issued an address or delivered a notification.

## What and why

A delivery token identifies a provider endpoint, not the person using the app.
The device can remain installed while a user signs out, another signs in or the
provider rotates an address. Conflating these lifetimes lets a delayed callback
restore an old session binding or silently transfer ownership.

Keep the latest admitted address in app/process state. Keep request progress and
errors with the presenting screen. Let explicit registration snapshot the current
address into an exact-session command. Once sent, that command retains its own
input while newer callbacks only change the candidate for a future action.

Example (Conceptual):

```text
collect address A → explicit register(A, session X) starts
provider rotates to B → local candidate B; no backend write
register(A) acknowledgement is uncertain → read current registry
user explicitly registers B under the current session after reconciliation
```

Permission to display alerts is another independent value. It can change in
Settings without invalidating an address. Do not synthesize a token from
permission, infer permission from collection, or promise delivery from a saved
registry record.

## Gotchas and alternatives

Native callbacks may carry no request identifier. A process callback can update
the current candidate after a screen's waiter ends; it cannot acknowledge an
unrelated authenticated operation. SDK request completions may race callbacks.
A newer local revision should win over an older captured result.

The SDK can own durable token storage and retries even when the app's candidate
is ephemeral. Cancellation ends an app waiter, not necessarily provider work.
Automatic registration can be a future product policy, but needs durable intent,
lease replacement, rotation reconciliation, opt-in and failure semantics. It is
not a harmless onNewToken side effect.

The distinction is used by both catalogs' optional provider adapters and the
existing installation owner. Next: define eligible recipient selection and
version-guarded invalid-token cleanup before implementing a sender.

Related: [installation identity](installation-identity-and-current-state-reconciliation.md),
[device notifications](device-notifications-and-navigation-hints.md) and
[construction/evidence](../../docs/blueprints/push-tokens.md).
