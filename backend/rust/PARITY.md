# Go / Rust backend parity

Assessment: 2026-10-18. Rust is a memory/PostgreSQL foundation candidate, not a replacement
for the current Go API or a registered CLI starter.

| Capability | Rust status | Evidence / next boundary |
| --- | --- | --- |
| Workspace and process composition | Implemented | Eighteen crates; Rust 1.86, locked builds, format/Clippy checks |
| Expected failures and public problems | Implemented for individual classified failures | Eleven shared projection fixtures across all ten kinds; internal diagnostics/code/fields/causes stripped |
| Health and canonical fallback routing | Implemented | Shared HTTP corpus runs against actual compiled Go/Rust processes |
| Request/correlation metadata, HEAD/method errors, private-input logging | Implemented for admitted requests | Shared corpus plus Rust panic/log checks |
| Readiness, active drain, forced task cleanup | Implemented in Rust | Actual listener tests; process runner checks clean SIGTERM/listener closure in both languages |
| Startup configuration | Supported subset | Rust rejects unsupported selections; literal addresses only |
| Clock and identifier ports | Implemented | Injectable business wall clock, canonical IDs, fallible OS entropy and request-boundary injection; 11 shared ID cases |
| JSON/version/key admission | Implemented for shared corpus | 26 JSON and 27 header cases, owned 413/415/428, bounded body reads and pre-encoded responses; endpoint DTO caveats below |
| Full routing and network budgets | Partial | See limits below; no Go path normalization or full read/write/idle policy yet |
| Identity core and recovery | Memory/PostgreSQL verified subset | Register/sign-in/renew/sign-out/me, verified contact, reset/change, private SMTP, credential v1/v2 and lifecycle Audit facts; limits below |
| Account | Memory/PostgreSQL core verified | Create/personal bootstrap, owner pages/selection, strong-CAS rename and consumer-owned credential adapter; 40 shared wire cases and process concurrency/isolation |
| Audit | Memory/PostgreSQL subset verified | Account/personal HTTP pages, strict event admission and atomic evidence/inbox; 61 event, seven access-response and 24 HTTP shared cases including recovery/Files |
| Notes | Memory/PostgreSQL verified subset | Account-authorized CRUD, immutable scoped command receipts, concurrent CAS/replay and 47 shared HTTP cases; durable handoff below |
| Files/Media | Memory/PostgreSQL verified subset | Metadata lifecycle, optional keyed receipts, immutable memory/directory objects, binary HTTP and independent Audit; evidence below |
| Notifications/inbox | Memory/PostgreSQL verified subset | Independent Files consumer, delivered/suppressed markers, scoped paging/counts, first-read and media preferences; evidence below |
| Installations/session retirement | Memory/PostgreSQL verified subset | Independent proof/CAS/target registry, sanitized HTTP and atomic Identity v1/v2 retirement; evidence below |
| Push jobs/providers | Planned | Copied 0003/0004 artifacts only; no Rust sender/eligibility/jobs |
| Synchronous capability registry | Implemented subset | Immutable assembly, requirements/caller admission, bounded JSON/deadlines/public failures; credential v1/v2 and account.access.v1; remote routing remains planned |
| Outbox/inbox delivery | Memory/PostgreSQL subset verified | Account/Identity state plus envelope commits; stable IDs, independent consumers, bounded retry, exhaustion/readiness, lost acknowledgment and owned shutdown |
| Broadcast and WebSockets | Planned | No Rust live transport or recoverable client hints |
| PostgreSQL and explicit migrations | Core subset verified | Owned pools/state/outbox/inbox, history checks, restart/replay and Go core handoff; see acceptance below |
| Metrics, deployment and backup | Planned | No Rust operational product profile |
| Native connected profile and CLI extraction | Planned | Requires explicit inventory/selection and independently generated product acceptance |

## Scope of shared checks

[Fixtures](../../contracts/fixtures/backend-runtime/README.md) cover canonical,
well-formed HTTP paths, individual classified failures, JSON/header admission and
canonical identifiers. Each language is
checked against the same expected values, not merely against the other language.
The runner replaces inherited application configuration, builds both APIs, owns
temporary processes/loopback ports and removes its output after shutdown.
It does not modify Go production behavior or expose test-only diagnostic routes.

Request IDs are independently generated UUID v4 values. Tests compare validity,
freshness and body/header consistency rather than equating IDs between servers.
Date, header order, JSON object member order and connection implementation are
not parity assertions. HEAD must retain representation headers with no body.

## Explicit remaining runtime differences

- Go ServeMux cleans/redirects paths and decodes segments. Rust currently matches
  canonical route patterns (Account IDs decode one segment). Other encoded paths, repeated separators, dot segments,
  native parser rejection and redirects are outside this first shared corpus.
- Rust config accepts literal socket addresses; Go also accepts hostnames and an
  empty bind host. Rust refuses unsupported Go module/provider selections.
- Hyper uses a five-second header-read timeout and a 16 KiB **read buffer** limit.
  That buffer is not Go's `MaxHeaderBytes` accounting. Go's separate ten-second
  request read, twenty-second write and sixty-second idle budgets remain gaps.
- The owned fifteen-second deadline stops cooperative Rust query futures; it
  cannot interrupt synchronous work that never yields. Identity scope checks guard
  its memory commits, as does Account; an interrupted response is not proof of rollback.
  Go's request context cancellation is also cooperative; future command adapters
  must preserve uncertainty when dropping a request does not undo an effect.
- Rust catches unwinding panics while assembling buffered responses. Process
  composition replaces the default panic hook with a redacted hook. Abort-mode
  panics, streaming bodies and errors after response commitment are not covered.
- Rust failures retain a private source, but there is no Go-style joined-error
  aggregation/condition identity implementation. Version/key parsing does not
  establish atomic CAS, command receipts, replay, retry, auth or persistence.
- JSON normalizes duplicate object members to their last value before typed
  decoding and rejects ignored fields recursively through Serde. This is not
  complete Go DTO equivalence: Go can reject an earlier wrongly typed duplicate
  even when its final value is valid, merge repeated struct objects, match field
  names case-insensitively and handle null/invalid Unicode/numbers differently.
  Shared tests cover an optional string/nested bool DTO. Each business endpoint
  must establish its own field/duplicate policy before claiming native parity.
  MIME parameter edge cases outside the corpus also need endpoint admission review.
- Business Clock returns SystemTime; deadlines retain Tokio's monotonic clock.
  Identity consumes Clock for issuance/rotation, independently of HTTP timers. ID
  allocation is injected into the real boundary/server and entropy failures
  produce generic 500 without inventing a request ID or running the handler.

## Identity scope

[Core Identity](../../contracts/behavior/rust-identity.md) now has domain values,
application ports, named commands/query, explicit memory/security adapters and
versioned HTTP transport. Its endpoint decoder preserves Go's ordered string
duplicates, case folding and null no-op rather than generic Value normalization.
Malformed Unicode repair and uncaptured MIME/router edge cases remain differences.
The wire authority ID stays `go.identity.v1` for protocol compatibility; stores
and credentials are independently owned by each process.

Run `make backend-identity-parity-test`: shared policy and PHC/token fixtures,
17 wire cases, core session flows and socket-source gate checks pass against both
real APIs. Rust additionally exercises atomic failure, registration/renewal races,
dummy verification, restoration/time bounds and canceled-caller KDF lifetime.
The two-job crypto adapter fences/joins work after HTTP drain, including jobs
whose request future was dropped; total process shutdown is not capped by the
HTTP-only ten-second budget.

Rust has no general module lifecycle registry or selected maintenance/retention
tooling. Recovery now has the separate acceptance below. Selected delivery/reset
probes now extend serving readiness. Memory Audit is not durable evidence;
native/CLI Rust selection is still unregistered.

## Account and synchronous credential scope

[Account behavior](../../contracts/behavior/rust-account.md) now establishes the
first inter-module question: the Account edge consumes the narrow shared Principal
resolver, implemented through `identity.credential.resolve.v2`. Its crate never imports
Identity. Both credential versions remain registered for `api`; v1 retains its
principal-only response, v2 supplies trusted session ID and absolute expiry.
Missing provider, denied caller, malformed evidence and dependency failure stay
distinct failures with no fallback. Assembly freezes before serving and checks
its required provider. The Invoker port supports future adapter replacement,
but no cross-process implementation is present.

Run `make backend-account-parity-test` for seven shared name cases, forty HTTP
cases, live owner pagination, eight concurrent bootstraps, competing CAS writes,
renewal/revocation, default denial, private logs and owned process shutdown in
both languages. Rust tests additionally cover snapshot admission, fact failures,
concurrent memory commits, provider validation and deadline cleanup. The workspace
has a separate ignored database test exercised only by the disposable owner. AccountAccess and Audit delivery now have their own
acceptance below. Membership roles remain unimplemented; core SQL adapters are covered below.

## Committed events and Audit scope

[Memory delivery/Audit](../../contracts/behavior/rust-audit.md) now maps producer
facts before commit, retains state/outbox under one owner lock, and independently
records selected evidence/inbox. The event bus and dispatcher have replaceable
ports; one consumer failure does not skip another. Stable-ID replay after a lost
acknowledgment preserves one Audit entry and its first recording time. Workers
use 100 ms polling, 32-record batches, a two-second publication budget and five
failed attempts before retained exhaustion. Readiness includes selected workers/
producer delivery; HTTP drains before all workers stop/join, then crypto closes.
Canceled close retains its join handle rather than detaching work.

`make backend-audit-parity-test` runs 33 shared event admissions, seven strict
access-response cases, 24 live HTTP cases and real Account/personal evidence
flows against both APIs. Rust tests additionally cover atomic mapping failure,
replay/concurrency, timeout/cancellation, dependency failure, exhausted readiness
and cancellation-safe joining. Generic envelope time is a canonical typed
microsecond value; arbitrary non-UTC/submicrosecond wire admission is not added.
Go joined-error chains are not reproduced. No general dynamic module lifecycle
framework, broker, retention or native/CLI
selection is implied. PostgreSQL claims/restart recovery have separate acceptance below.

## PostgreSQL core acceptance

[Durable core behavior](../../contracts/behavior/rust-postgres.md) selects SQLx
0.8.6 behind existing ports. Account, Identity, Audit, Notes, Files and Notifications own separate pools/SQL;
process composition alone imports the local migration catalog. Histories retain
Go's exact current checksums, including additive DDL for unsupported workflows.
Recovery and Files actions now have explicit admission and independent acceptance.

`make backend-postgres-parity-test` passed against an isolated local PostgreSQL
14.24 cluster: concurrent/file-atomic migrations and drift/future refusal,
bootstrap/CAS races, state/outbox and evidence/inbox rollback, refresh reuse,
lease takeover/stale acknowledgment, quarantine, cursor locking and canceled
transaction cleanup. Real APIs then exercised core Audit flows, SIGKILL/restart,
pending-event recovery, Rust→Go→Rust persisted accounts/session renewal and
Go-created inbox fingerprints replayed without duplicates. Module database
overrides have a separate real six-database acceptance phase including Files/Notifications.

Existing memory runtime/Identity/Account/Audit conformance remains separate and
passes unchanged. Default workspace tests ignore the owned database test; run
the explicit runner for durable evidence. PostgreSQL failover/COMMIT-wire-loss,
backup/deployment, unsupported business modules and CLI/native starter selection
remain unverified/unimplemented. Recovery challenge handoff has additional
acceptance below. Rust-backed native connection needs independent acceptance.

## Notes scope and durable command evidence

[Notes](../../contracts/behavior/rust-notes.md) implements the shared backend
receipt contract through consumer-owned Store/AccountAccess ports, an optional
Changes port, typed domain decisions and independent memory/PostgreSQL adapters.
The process mounts Notes with the selected credential resolver and separately
owned pool/migration history. It selects no Notes signal/broadcast adapter.

`make backend-notes-parity-test` passes eleven title cases, four fixed v1 command
fingerprints and 47 HTTP cases against Go/Rust, plus twelve concurrent matching
creates, one CAS winner, replay after deletion, receipt lookup, scoped paging,
revocation and private-input logs. Rust adds access-response admission, required
authorization on replay, allocation failure and post-success hint ordering.
The PostgreSQL runner repeats provider conformance, injects finalization failure
and an incomplete claim, kills/restarts the API, and replays Rust-created receipts
in Go and Go-created create/update/delete receipts in Rust. Historical evidence
survives later deletion; changed input never becomes another successful command.
Separate Notes database selection also passes a real authorized write/read.

Notes has no Audit facts, durable native outbox/sync, Rust WebSockets or selected
native/CLI Rust product profile. Backend wire compatibility does not establish
those independently generated or connected-product workflows.

## Identity recovery and private SMTP

[Recovery](../../contracts/behavior/rust-recovery.md) implements canonical unique
verified contacts, purpose-separated twenty-minute single-use codes and
password reset/change behind RecoveryStore/Mailer ports. Password checkpoints
are compared under the principal lock; session issuance serializes with mutation.
Challenge consumption, password replacement, all session revocations and required
facts commit together. New revocation reasons use v2; v1 remains closed. Audit
admits the three principal lifecycle facts and both revocation schemas without
changing existing fingerprints.

`make backend-recovery-parity-test` passes twenty email, fifteen event and
twenty-one HTTP cases plus actual Go/Rust local-SMTP flows. Rust adds memory
transition/failure/race checks, bounded queue shutdown and TLS/STARTTLS hostname
acceptance/refusal. `make backend-postgres-parity-test` additionally proves
outbox-failure rollback and unconsumed Rust verification → Go consumption →
Go reset → Rust consumption across a killed process, with retained Audit output.
Existing Identity 0002 and Audit 0004 already supply the schema; no applied SQL changes.

SMTP is explicitly selected through private `IDENTITY_MAIL_CONFIG`. TLS uses
system roots, bounded concurrency/replies/deadlines and no automatic resend.
The anonymous 64-slot queue performs lookup/mail outside public response work;
202 promises ephemeral admission only. Owned queue and delivery tasks are fenced
and joined before KDF/pool closure. Expired-challenge pruning exists as a bounded
store operation, without a selected CLI/schedule.

Limits: no live mail-provider acceptance, durable mail queue, contact replacement,
MFA/passkeys/OIDC or distributed abuse controls. Notifications retirement now has
its own consumer and acceptance below.
Native/CLI Rust products remain unregistered; this is still a foundation candidate.

## Files lifecycle, immutable bytes and durable handoff

[Files](../../contracts/behavior/rust-files.md) owns private metadata, named
commands/queries and consumer-owned AccountAccess/Store/Objects. Memory and SQL
commit metadata, optional keyed receipt and required outbox fact atomically.
Replay precedes CAS/existence and current authorization remains required. The
fixed files.command.v1 encoding and capitalized persisted snapshots/receipts
match Go. Deletion commits the tombstone before object cleanup; exact replay
repeats cleanup, while receipt reads remain observational.

`make backend-files-parity-test` passes twenty-four specification cases, four
fingerprints, thirteen event and forty HTTP cases plus oversized binary
admission. Actual APIs exercise concurrent request/completion replay, immutable
PUT/download/HEAD, historical receipts after deletion, scope isolation/pagination
and causal Audit. Rust additionally tests required-fact and cleanup failure,
corrupt-byte refusal, directory reopen and canceled-caller/close ownership.
`make backend-postgres-parity-test` proves outbox/finalization rollback and the
deferred completeness guard, then continues a killed Rust upload in Go, reads
Go's completion in Rust, replays Audit without duplication, and replays Rust's
deletion/historical receipt in Go with the same private object directory.

SQL owns the separate Files pool/history; FILES_DATABASE_URL falls back to the
common URL. FILES_DIRECTORY defaults to .data/files. Directory jobs are bounded
at four, tracked across cancellation and joined before pools close. Memory
delivery preserves per-file order; PostgreSQL uses existing leased stream guards.
Audit retains optional file_id, omitting it from old fingerprints when absent.
Notifications now consumes completion independently. Cloud objects, processing,
orphan sweeping, retention, automatic sync and native/CLI Rust products remain separate.

## Notifications inbox and committed suppression

[Notifications](../../contracts/behavior/rust-notifications.md) now owns its
Item domain, named command/query handlers, consumer-owned AccountAccess/Store
and independent memory/PostgreSQL adapters. Only upload completion v1/version 2
maps to the source actor's in-app inbox. No Files/Account crate/store is imported.
Source marker, preference decision, optional item and per-scope sequence commit
together; suppressed sources remain consumed after unmuting. Lists/counts/read
changes and preference assignments share the scope guard. First read is retained;
settings/read mutations have no receipt/CAS or automatic replay.

`make backend-notifications-parity-test` passes twenty-two event cases, four
source fingerprints and forty-three HTTP cases plus actual Go/Rust upload/inbox,
mute/unmute, scope isolation, first-read, paging/filter/cursor and private-log flows.
Rust conformance adds sixteen-way replay, changed-source conflicts and concurrent
count/row snapshots. `make backend-postgres-parity-test` injects sequence/item/
marker/read/settings faults, runs six separate owner databases and kills Rust
with a pending Files source. Go consumes it as suppressed; replay across Go/Rust
retains suppressed/delivered decisions, first read and preferences without duplicates.

NOTIFICATIONS_DATABASE_URL falls back to DATABASE_URL and owns a separate pool.
All four Go migration files are retained unchanged; installations now uses 0002
through the separate acceptance below. Push DDL alone adds no sender/jobs.
Existing producer workers own delivery, with no new timer. Remaining scope includes
push providers/jobs, retention/admin replay, richer audiences, live hints and
native/CLI Rust acceptance. Rust times use the admitted 1970–9999 UTC microsecond
range rather than every arbitrary Go domain time.Time value.

## Installation registry and session retirement

[Installations](../../contracts/behavior/rust-installations.md) adds trusted
credential-v2 actors, separate hashed possession proofs and private target state
behind Notifications-owned ports. Proof/CAS plus a new registration ID permits
explicit rebinding; another live installation cannot claim the same target.
Successful expired-target reclamation erases the prior token in the same commit.
There are no registration receipts or automatic retries. Sanitized views expose
stored registered/current_binding metadata, not live send eligibility.

The independent notifications.identity receiver admits closed v1/v2 revocation
reasons. Matching binding cleanup, session tombstone and exact source fingerprint
commit together under one owner guard. Late old-session delivery leaves a rebound
installation intact, and retained tombstones refuse delayed old-session creation.
No Identity domain/store or foreign SQL enters Notifications. Memory is bounded;
PostgreSQL selects the existing separate Notifications pool/0002 history.

`make backend-installations-parity-test` passes ten proof, twenty spec/target,
twenty-one event/fingerprint and forty-two HTTP cases against Go and Rust. Real
flows cover rotation/rebind, uniqueness, unregister, renewal and all four session
revocation reasons, using local SMTP for recovery codes. Shared memory/PostgreSQL
conformance checks twelve-way creates, competing targets, register/retire races,
replay/input conflicts, expiry and canceled writes. `make backend-postgres-parity-test`
rejects cleanup/tombstone/marker writes separately and fails a claim after expired
target cleanup, proving rollback. Six separate databases exercise registry and
retirement without co-located Identity tables. Pending v1/v2 producer facts survive
SIGKILL and Go↔Rust handoff; marker replay preserves newly rebound registrations.

Tokens remain plaintext in the private store. Disabled rows/proofs/tombstones/
markers have no pruning policy. No provider-issued token, eligibility capability,
external sender/jobs, native Rust connection or generated CLI profile is included.
Rust remains a foundation candidate.
