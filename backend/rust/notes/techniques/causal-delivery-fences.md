# A later stream event can fence an earlier consumer decision

Claim: a later event in a strictly ordered producer stream can establish that
all selected consumers finished an earlier event, without adding a test-only API.

Origin/evidence: 2026-10-18 [Notifications process runner](../../../../scripts/notifications_parity.py),
[Files stream ordering](../../internal/files/src/infra/memory.rs) and leased SQL
outbox behavior, observed against actual Go/Rust processes.

Seeing upload completion in Audit alone does not prove Notifications finished it.
The bus can call Audit first and Notifications later, and each consumer commits
independently. A test that immediately unmutes after an Audit read could therefore
race the first preference decision and mistake legitimate backlog behavior for
a broken suppression rule.

The runner instead completes a file while muted, deletes that same file and waits
for its deletion fact in Audit. Files will not publish the deletion until the
earlier completion's publication succeeds, which requires all selected consumers.
Notifications is subscribed to completion, while Audit also observes deletion.
The later same-file observation therefore fences the earlier completed decision.
It does not make Audit and Notifications one transaction.

This technique is used in the real HTTP mute/unmute flow. Direct store conformance
and the disposable database phase separately prove suppressed replay. The latter
kills Rust while its consumer is unavailable, consumes the retained source in Go,
then replays sources in both languages and compares their retained inbox state.

Gotchas: the fence depends on per-stream order and acknowledgment covering every
selected consumer. Another file/account stream, source timestamp or fixed sleep
does not provide that evidence. Exhausted earlier delivery can block the stream.
This is acceptance reasoning, not a public cross-module completion guarantee or
a general flush protocol. Run make backend-notifications-parity-test.
Related: [consumer decisions](../patterns/consumed-suppression-and-inbox-snapshots.md),
[durable handoff](durable-restart-and-language-handoff.md) and
[Files walkthrough](../modules/internal/files/README.md).
Next: what acknowledgment semantics would a broker adapter need to preserve this test?

