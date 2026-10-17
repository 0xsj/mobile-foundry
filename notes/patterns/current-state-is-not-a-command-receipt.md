# Current state is not a command receipt

Claim: a fresh read can make another decision safe without proving what happened
to the previous write.

Origin: the native workspace slice, 2026-10-17, reuses Go Account's existing
non-idempotent create and CAS rename. Source inspection of
[the HTTP edge](../../backend/go/internal/account/transport/http/v1/handler.go)
and [its contract](../../contracts/http/account-audit-v1.md) establishes that
Idempotency-Key is refused and no Account command receipt exists. Native tests
exercise lost replies, stale versions and late provider completions. Independent
execution evidence belongs in [the construction guide](../../docs/blueprints/native-workspaces.md).

## What and why

A rename response can disappear after commit. GET may show the requested name,
but another writer could have produced that state. The observation supplies a
new version for an explicitly reviewed decision; it cannot label the earlier
command as confirmed. Conversely, a stale rename is refused by If-Match even if
an earlier read succeeded. Read-before-write is useful together with atomic CAS.

Creation is harder: an owner listing can contain several accounts with the same
name. Absence from a live UUID-ordered page proves little. The UI therefore
requires review plus an explicit duplicate-risk choice before another create.
It never silently resends the earlier request or invents a matching receipt.
Every create attempt consumes the review acknowledgement; a second uncertain
create cannot reuse the first review. Both owner suites cover that boundary.

Conceptual sequence:

```text
PATCH account (If-Match: "2") → commit v3 → reply lost
GET account → current v3 → user reviews → new PATCH (If-Match: "3")
```

The second PATCH is a new decision, not recovery of an old command identity.
For Notes, the separate command/receipt contract permits checking the original
ID and explicitly retrying the exact original input. Do not generalize those
guarantees to every HTTP write.

## Gotchas and actual use

Cancellation requests cooperation; a provider may complete anyway. WorkspaceModel
checks generation and lease, while Swift defer/Kotlin finally preserve uncertainty
before clearing the in-flight create flag. An observed Kotlin state-test failure
found that early-return cleanup could otherwise discard the warning when a lease
changed without a composition rebind. The corrected check records uncertainty
for the original owner without publishing success into the new session.

The warning is transient, bounded to the last interrupted create owner. It is not
a durable intent log. After process death, explicit review still matters. Workspace
rename does not change a data namespace: the account ID and current Notes lease
stay the same, so metadata editing must not clear an unrelated pending command.

Related: [durable intent](persist-intent-before-external-effect.md),
[registration and sessions](registration-is-not-session-issuance.md),
[cached checkpoints](cached-content-and-command-checkpoints.md),
[Account behavior](../../contracts/behavior/native-workspaces.md).
Next: should a product require durable Account command receipts, and which
atomic store owns the receipt and its required integration fact?
