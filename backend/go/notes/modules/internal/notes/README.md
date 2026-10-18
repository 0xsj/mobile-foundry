# Notes command/query and receipt walkthrough

Rust follow-up, 2026-10-18: [shared Notes checks](../../../../parity/notes_test.go)
and [the process runner](../../../../../../scripts/notes_parity.py) now verify
47 HTTP cases and real receipt/CAS/scope flows in both implementations. The
disposable PostgreSQL runner additionally checks Go↔Rust replay, including
HTML/separator/NUL fingerprint bytes and historical receipts after deletion.
See [Rust Notes](../../../../../rust/notes/modules/internal/notes/README.md).

Claim: one module can authorize through a consumer-owned capability and atomically
persist its own data/receipt without importing another domain's implementation.

Origin: remote Notes slice, 2026-10-13. Evidence: Go import policy, vet/build/race
tests, memory/PostgreSQL conformance, and compiled API kill/restart acceptance.

## Source reading order

1. [Values and pure decision](../../../../internal/notes/domain/note.go): canonical
   IDs, preserved bounded titles, versioned record and immutable receipt values.
2. [Consumer ports and authorization](../../../../internal/notes/app/ports.go),
   [write command](../../../../internal/notes/app/command/write.go), and
   [read queries](../../../../internal/notes/app/query/read.go). A known create
   replay avoids allocating a fresh ID, but the store still owns the atomic check.
3. [Account capability adapter](../../../../internal/notes/infra/capabilities/account.go):
   serializes the consumer's request and validates a real boolean reply. Access
   dependency failure propagates; false is not a fallback for unavailable IO.
4. [Memory store](../../../../internal/notes/infra/memory/store.go) and
   [PostgreSQL store](../../../../internal/notes/infra/postgres/store.go).
5. [HTTP boundary](../../../../internal/notes/transport/http/v1/handler.go),
   [module composition](../../../../internal/notes/module.go), and
   [owned pool builder](../../../../internal/notes/postgres.go).
6. [Migration](../../../../migrations/notes/0001_notes_receipts.sql) and
   [provider/HTTP checks](../../../../tests/notes_test.go).

## What and why

The Notes Store port takes admitted inputs and returns a receipt. Memory guards
the whole operation with one mutex. PostgreSQL first INSERTs a receipt claim
under a composite unique key. A duplicate waits for the competing transaction;
the next ReadCommitted SELECT observes the winner and compares its v1 command
fingerprint. A new claim locks the target row, calls the shared pure decision,
mutates the record, then fills the receipt before commit.

The deferred constraint trigger queries the final row, rather than the INSERT's
initial NEW value. It therefore permits an incomplete claim inside a transaction
while refusing one at commit. Any statement/receipt failure rolls both back.
No foreign Account/Identity tables are referenced. Account's capability supplies
current owner access; Notes uses that policy for all scoped data operations.

## Example — Excerpt

```go
_, found, err := h.Store.Receipt(ctx, account, actor, input.CommandID)
if err != nil { return domain.Receipt{}, err }
if !found { candidate, err = h.IDs.NewID() }
```

This early read avoids allocation for a known replay; it is not the uniqueness
mechanism. A concurrent winner still requires Store.Apply's transactional claim.

## Verification and gotchas

Twelve matching creates yield one record/result; twelve version-two edits yield
one CAS winner. Receipt replay continues after physical deletion. Account/actor
keys prevent receipt lookup leakage. An injected receipt-finalization CHECK
failure leaves neither a note nor a claim. A killed PostgreSQL API restarts with
the same receipt and replay result. Isolated local PostgreSQL 14.24 was used in
this batch; PostgreSQL 18.6 Docker execution was unavailable because the Docker
VM storage is read-only. Earlier provider evidence is recorded separately.

bytea avoids PostgreSQL text/jsonb's NUL limitation. Decoding validates receipt
and row values; corruption is a failure, never an empty list. Fixed fingerprint
field encoding must survive internal struct refactors. Current receipts have no
expiry/pruning policy, and memory receipts are transient. Notes changes are not
yet selected as Audit facts. Real DB commit-acknowledgement network cuts, physical
device performance and capacity/retention behavior were not measured.

## Related and next questions

[Shared recovery pattern](../../../../../../notes/patterns/command-receipts-and-uncertain-writes.md),
[receipt contract](../../../../../../contracts/behavior/remote-notes.md),
[transactions and leases](../../../patterns/transactions-leases-and-committed-cursors.md).
Next: persist native pending input before dispatch; choose receipt retention;
design conflict reconciliation and selected Notes facts independently.
