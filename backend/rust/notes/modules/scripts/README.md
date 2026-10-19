# Owned operational acceptance and paired backups

Claim: a recovery check must restore the private database/object pair into an
unused namespace and then exercise retained behavior through the API.

Origin/evidence: 2026-10-18, copied deliberately from Go's portable drivers, with
Rust image identity and actual edge/upgrade plus damaged-backup checks added.
Read [backup.py](../../../scripts/backup.py) and
[operations_acceptance.py](../../../scripts/operations_acceptance.py).

backup retains mobile-backup.v1: stop the selected API, dump common PostgreSQL,
archive private objects, verify paths/types/UID/GID, publish SHA-256 manifest last,
then resume only an API that was running. Restore verifies first, refuses existing
project/resource names, restores to an unused namespace, checks histories and
leaves API stopped for review. An incomplete target remains for inspection.

The driver owns temporary env files, a locally built/selected image and disposable
Compose projects. It chooses non-overlapping networks and separates static edge
from dynamic addresses. Finally cleanup removes only those owned projects/volumes.
The optional --image belongs to the caller and is retained.

Example (Conceptual): a retained Notes receipt and file bytes survive restore;
a pending reset challenge is consumed once, then its old session is refused.
Passing only a database health query would miss each of those behaviors.

Verification: actual container/mail/edge/metrics, quiesced backup, unused-project
restore, damaged checksum/existing-target refusal, Notes/Files/contact/challenge,
revocation and maintenance. API logs omit test passwords/codes/credentials.
Local Mailpit and HTTP edge do not prove mailbox delivery/public TLS. The common
database/private filesystem profile does not back up split databases/cloud objects.
Protect archives as active credential-bearing data; checksums are not authenticity.
Related: [paired snapshot pattern](../../patterns/quiesced-database-and-object-backups.md).

CLI follow-up, 2026-10-18: [smoke](../../../scripts/smoke.py),
[PostgreSQL acceptance](../../../scripts/postgres_integration.py) and
[disposable database ownership](../../../scripts/postgres_owner.py) now import
only standard-library/copied local helpers. API package selection reads the copied
Cargo manifest, so renaming does not leave a repository-specific package selector.
The PostgreSQL owner is adapted from Go's disposable driver, not its Go application
tests. It never consumes an app DSN: copied Rust store/job tests and real APIs
share a newly owned cluster. Exact receipt replay, private bytes, pending Files
notification and persisted sign-out survive killed/restarted processes. A generated
project executes these drivers without the original checkout; read
[starter construction](../../../../../docs/blueprints/rust-starter.md).
Next: what additional owners would an off-host or split-database backup coordinate?
