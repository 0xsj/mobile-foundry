# Files lifecycle walkthrough

Claim: versioned metadata can commit with receipts/facts while immutable external
bytes remain behind a separate port with an explicitly recoverable lifecycle.

Origin: Files slice, 2026-10-17. Source, memory/PostgreSQL execution, native HTTP
and independent extraction evidence are separated in
[the construction guide](../../../../../../docs/blueprints/files-media.md).

## Source reading order

1. [Domain](../../../../internal/files/domain/file.go): canonical Spec admission,
   private File state, detached Snapshot and pure completion/deletion transitions.
2. [Ports](../../../../internal/files/app/ports.go),
   [application errors](../../../../internal/files/app/errors.go) and
   [request handler](../../../../internal/files/app/command/request_upload.go).
   Named commands own orchestration; use-case absence/CAS/key conflicts stay in app.
3. [Completion](../../../../internal/files/app/command/complete_upload.go),
   [content upload](../../../../internal/files/app/command/upload_content.go) and
   [delete](../../../../internal/files/app/command/delete_file.go) distinguish object
   effects from guarded metadata writes.
4. [Memory](../../../../internal/files/infra/memory/store.go),
   [PostgreSQL](../../../../internal/files/infra/postgres/store.go),
   [fixed receipt encoding](../../../../internal/files/infra/records/codec.go) and
   [migration](../../../../migrations/files/0001_records_receipts_outbox.sql).
5. [Objects](../../../../internal/files/infra/objects/objects.go): immutable memory
   and directory publication. IDs generate storage names; filenames never do.
6. [Producer](../../../../internal/files/infra/events/producer.go),
   [leased delivery](../../../../internal/files/infra/postgres/outbox.go),
   [Audit receiver](../../../../internal/audit/infra/events/files.go),
   [HTTP](../../../../internal/files/transport/http/v1/handler.go) and
   [composition](../../../../internal/files/module.go).

## What and why

A receipt claim serializes same-key requests. A competing PostgreSQL INSERT waits;
its next ReadCommitted SELECT observes the committed fingerprint/response. The
new write locks the record and applies app.Plan/domain transitions, then commits
snapshot, receipt and outbox together. A deferred trigger rejects incomplete
receipt claims. Generated file/event IDs and time are excluded from intent hashes.

Completion reads/verifies objects before CAS. A raced delete is still decided by
the store, not that preliminary read. Deletion commits its tombstone before
object removal, so a cleanup failure cannot leave downloadable metadata pointing
to removed bytes. Same-key retries repeat physical cleanup without another fact.
A late PUT rechecks metadata and cleans up if it observes deletion. A crash between
these steps can leave inaccessible orphan bytes; a future sweeper owns recovery.

Conceptual:

```text
RequestUpload → metadata + receipt + outbox
PUT content → immutable object bytes
CompleteUpload → verify object → metadata CAS + receipt + outbox
DeleteFile → metadata tombstone + receipt + outbox → object cleanup
```

## Gotchas, use and verification

The directory is trusted private application storage. Atomic temp/link publication
and fsync do not create a transaction with PostgreSQL. Retain both stores; a
metadata backup alone is insufficient. The content type is declared, not scanned.
The current owner policy admits reads/writes; adding roles needs deliberate action
checks. A replay is historical evidence, not current download authorization.

Used by the server, CLI backend and native Go session gallery. Relevant checks:
[provider/object/HTTP cases](../../../../tests/files_test.go),
[disposable process checks](../../../../scripts/postgres_integration.py) and
[shared native runner](../../../../../../scripts/go_native_test.py).
Memory and PostgreSQL 14.24 tests, process SIGKILL recovery and real native binary
transfers passed. Build/import checks do not establish push, S3, processing,
operational retention, power-loss safety or generated native connectivity.
Related: [object lifecycle pattern](../../../patterns/metadata-and-object-lifecycles.md)
and [communication](../../../patterns/ports-and-module-composition.md).
Next: how should abandoned objects/metadata be retained and reconciled without
holding database locks across slow storage calls?
