# Historical receipts before current state

Claim: command replay must compare immutable original input before consulting
mutable current state if a retry is to return the same committed outcome.

Origin/evidence: 2026-10-18 Notes memory/PostgreSQL conformance and actual Go/Rust
HTTP processes, including concurrent matching creates and replay after deletion.

One command has a stable caller-generated identity and admitted operation, target,
title and expected version. Successful execution stores that input fingerprint
and a receipt in the same transaction as its state change. The receipt belongs
to an account/principal/command tuple. A later matching command reads that evidence
first: later edits, stale versions and record deletion cannot change its outcome.
Changed input under the same scoped key is a conflict, rather than another write.

Example (Conceptual): create C commits version one, update U commits version two,
and delete D removes the note. Replaying C returns its version-one receipt; it
does not recreate anything. Looking up the current note returns absent. Both are
correct because receipt history and current state answer different questions.

Authorization still precedes replay. A historical receipt grants no access after
the principal loses authority or signs out. Missing receipt is also not proof of
rollback: the original request could still be executing. Preserve the original
command until an explicit recovery decision can settle its outcome.

A preliminary receipt read may avoid allocating a create target, but it cannot
serialize concurrent commands. The store must claim the unique receipt key,
compare input and commit the mutation/receipt together. Failed execution must
roll back the claim so a later valid command can use it. A random create target
is an output and never belongs in the input fingerprint.

Gotchas: receipts retain historical titles and therefore require deliberate
privacy/retention policy. They are neither Audit records nor a durable device
queue. Use live reads for current state and explicit command recovery for uncertain
acknowledgments. Used in [Notes](../modules/internal/notes/README.md); related:
[state/outbox/inbox](state-outbox-and-inbox-boundaries.md) and
[JSON fingerprints](../substrate/go-json-command-fingerprints.md).
