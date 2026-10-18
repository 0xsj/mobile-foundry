# Rust account-scoped Notes and immutable receipts

Established 2026-10-18. Rust implements the backend portion of
[remote Notes](remote-notes.md) through independent domain, command/query,
consumer-owned ports, memory/PostgreSQL adapters and versioned HTTP transport.
Notes remains a replaceable example domain.

Every operation, including replay and receipt lookup, authenticates the trusted
HTTP principal and authorizes the original account through `account.access.v1`
as caller `notes`. Denial and dependency failure remain distinct. No Account or
Identity crate/store/SQL enters Notes. Receipts belong to the exact
`(account, principal, command)` scope.

The HTTP methods, paths, statuses, strong ETags, receipt Location, title admission
and page shape match the shared contract. Missing command UUID is invalid; no
If-Match is accepted for create. Update/delete require a strong observed version
below `2^53-1`. Delete is bodyless. Mutations, individual reads and receipt reads
reject any query, including a bare `?`. Collection pages allow unique, nonempty
`limit` and `after`, default twenty, maximum one hundred, ascending canonical UUIDs.

Titles preserve exact Unicode scalars, whitespace and NUL; they admit at most
two hundred scalars and require a character beyond ASCII space/tab/CR/LF. An
endpoint visitor preserves ordered duplicate decoding and case-insensitive title
fields. A null resets the optional title, matching Go's pointer-string DTO.
Malformed Unicode replacement and uncaptured MIME/path/parser behavior remain
the existing Rust boundary gaps.

A successful command commits the record change and immutable receipt together.
Replay compares original input before checking the current row/version. Matching
replay returns the original status, title, target and historical ETag, even after
subsequent edits or physical deletion; changed input is 409/notes.command_conflict.
Failed commands leave no successful receipt or reserved key. A missing receipt
is an observation, not proof that an in-flight request cannot commit.

Memory guards records and receipts with one mutex and loses them on process exit.
PostgreSQL owns a separate pool/schema and copied Go migration history. Under
Read Committed, it first claims the scoped receipt key, then locks the current
note for CAS. A competing claim waits and reads the winner in a new statement.
Finalization writes the receipt before COMMIT; the deferred trigger refuses
incomplete committed claims. Record titles and receipt bytes use bytea to preserve
NUL. The frozen `notes.write.v1` fingerprint includes Go's default HTML and
separator escaping; generated create targets never enter that input hash.

`NOTES_DATABASE_URL` overrides `DATABASE_URL` in PostgreSQL mode; an explicit
empty override fails. `make migrate` applies the selected Notes history;
`make migrate-check` and API startup only verify it. All workers drain before
any owned pool closes. Shared PostgreSQL rollback/uncertain-COMMIT rules apply;
there is no automatic command retry or native pending-journal selection here.

The application has an optional Changes port, invoked only after a confirmed,
validated store result (including matching replay). This process selects no
broadcast/Realtime adapter for it. Notes does not publish durable integration
facts or enter Audit. Socket hints and native/CLI Rust connection need their own
implementation and acceptance.

Verification: `make backend-notes-parity-test` checks eleven title cases, four
fingerprint vectors, 47 real HTTP cases and scoped/concurrent receipt flows
against Go/Rust memory APIs. Rust tests verify access-response admission,
authorization before replay, allocation failure, historical evidence, cancellation
and post-success optional hints. `make backend-postgres-parity-test` owns a fresh
database and checks concurrent receipt/CAS behavior, failed finalization rollback,
the deferred guard, restart and bidirectional Go/Rust replay after deletion,
plus distinct module database selection. No native UI/provider or standalone
generated Rust starter readiness follows from these backend checks.
