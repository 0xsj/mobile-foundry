# Live invalidation alongside pending writes

Claim: a recoverable live hint should queue an authoritative read behind accepted
work, never cancel or replay that work.

Origin: native identity/live slice, 2026-10-17. Evidence: matching Swift/Kotlin
owner tests and native HTTP/socket acceptance. Independent UI execution is tracked
in the linked blueprint; source checks alone do not establish it.

## What and why

Notes owns a pending command journal; LiveHints owns no records. Ready/changed
signals set one dirty flag. If an operation is active, it settles its write and
journal first. Completion consumes the flag and starts one read. A burst therefore
needs bounded state and no competing write lifetime. A later hint during that read
may request another read, without accumulating messages or canceling the operation.

Conceptual: create commits but its response is lost. A changed hint reloads the
visible Note while the original command remains pending. Seeing the Note is not a
matching command receipt. Only explicit receipt lookup or retry settles that intent.

## Gotchas and actual use

Fresh connection ready and foreground both refetch because hints are lossy. The
app must bind a new exact lease on account switch/renewal and stop old work on
background/sign-out. Cancellation alone is insufficient: generation/lease checks
also reject callbacks that finish late. Read failures stop until another hint or
explicit refresh; they never become write retries. This is not durable sync,
merging or background delivery.

The product tests send one hundred hints during an unconfirmed write, observe one
execution and one queued read, and retain the original pending command. UI checks
add an external writer rather than reusing the app's own post-write read as proof.

[Swift owner](../../templates/overlays/go-connected/swift/RemoteNotesStore.swift), [Kotlin owner](../../templates/overlays/go-connected/kotlin/RemoteNotesStore.kt); [construction/evidence](../../docs/blueprints/native-identity-live.md).
Related: [persist intent first](persist-intent-before-external-effect.md).
Next: define domain-specific merge/read-cache behavior before attempting broader sync.
