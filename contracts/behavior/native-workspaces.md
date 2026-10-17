# Native owner workspace management

Daily + go-connected selects AccountManagement on Swift and Kotlin. This extends
[Account writes](account-audit.md), [owned discovery](account-bootstrap.md) and
[native account access](account-access.md), using the existing
[Go v1 endpoints](../http/account-audit-v1.md). No backend schema/API changes.

AccountName admits the same explicit Go whitespace trim, 1–80 Unicode scalar
count and control exclusion on both platforms. Names retain their exact Unicode
normalization. Go session/directory observations reuse this canonical name
admission so a name accepted for create also remains selectable. Kotlin rejects unpaired surrogates before JSON encoding. Commands
and observations redact descriptions. ManagedAccount holds an owned account,
positive JSON-safe version and nondecreasing microsecond timestamps; it is not
a session grant. Generic IDs remain admitted native IDs; Go wire UUID rules stay
in HTTPAccountManagement.

The port has read/create/rename operations, one attempt each. Composition injects
an exact SessionLease-bound authenticated client and trusted principal. The Go
adapter requires status/shape/owner/ID/version/timestamp agreement and the strong
ETag matching the body version. Create confirms version 1, matching creation/update
times and exact name bytes. Rename sends the observed strong If-Match and admits
only the original ID/owner/creation time, version + 1, nondecreasing update time
and exact requested name. Neither operation sends Idempotency-Key. Missing or
malformed success can follow commit and remains unconfirmed.

Account → Manage workspaces provides fresh current-account read, rename and
create forms. A confirmed create leaves selection unchanged and blocks another
submission until a deliberate Prepare another workspace action. Choosing an
account remains the existing owner-guarded SessionController flow. Rename updates
observed display metadata without changing the current lease, rotating tokens or
rebinding Notes/pending intent. Restoration/renewal obtains current server metadata.

Only one management operation runs at a time. A conflict or unconfirmed write
clears rename readiness; a fresh read and user review precede another write.
Readback describes current state, not proof that a particular rename committed.
Unconfirmed creates additionally block creation. A fresh owner directory page,
review and explicit duplicate-risk choice can enable a new create. Each create attempt consumes that review; another
uncertainty needs another review. Listing is
live and cannot identify the outcome of an earlier command. Paging remains
explicit and bounded to one displayed page. No automatic mutation replay follows
readback, reconnect, renewal, reopening or process restoration.

Product owners check their generation and exact lease after suspension. Closing,
backgrounding or rebinding invalidates work. Interrupted creation keeps a
principal/deployment-bound transient warning; late completion cannot confirm a
result under a replacement lease. Cleanup must retain that warning even when
the operation returns early because the lease changed. The product retains the
last interrupted create owner in process memory, not a durable Account journal;
after process termination the user must review workspaces before submitting a
possibly duplicate create. Names/drafts are transient and cleared on dismissal
or session change. No Notes receipt/checkpoint is modified by account metadata.

Owner-only access is the current provider policy. Invitations, memberships,
roles, deletion, durable account command receipts, offline creation and automatic
sync require separate behavior. The catalog and other shell/provider selections
do not gain connected workflow readiness from these overlays.

Read [construction and execution evidence](../../docs/blueprints/native-workspaces.md).
