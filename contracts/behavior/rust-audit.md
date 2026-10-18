# Rust committed delivery and Audit

Established 2026-10-18. Rust implements the memory subset of
[Account/Audit](account-audit.md), [Account facts v1](../events/account-v1.md),
[core Identity facts v1](../events/identity-v1.md),
[Account access v1](../capabilities/account-access-v1.md) and the existing
[HTTP activity contract](../http/account-audit-v1.md). Go remains the reference.

Account and Identity map selected internal facts into integration envelopes
before mutation. The existing owner lock then commits aggregate/index/mapping
changes, the retained local fact and the required outbox record together. Event
identity equals the allocated retained-fact ID and stays stable on delivery.
Invalid metadata, payload mapping, capacity or pre-commit scope failure writes
neither state nor event. No foreign call occurs while a producer lock is held.

Envelopes carry canonical typed IDs, versioned name, producer, optional account,
actor, positive JSON-safe source version, UTC microsecond occurrence time,
request/correlation tokens and one bounded JSON data object (16 KiB). Account
events include only account/version. Identity includes principal, optional
session/version and a schema-admitted revocation reason. v1 admits
sign_out/refresh_reuse; v2 admits password_change/password_reset. Names, profiles, password
material and credential/digest values are excluded. Rust's typed time is already
canonical; arbitrary non-UTC/submicrosecond envelope wire admission is not added.

The immutable in-process bus selects explicit consumer identities and passes
owned envelope/payload clones to each matching consumer. An ordinary failure
does not skip another consumer. Cancellation stops further delivery. No consumer
is an unavailable failure. Rust reports the first failure rather than Go's joined
error chain; all attempted outbox outcomes are still retained. The Publisher
port allows a later transport adapter without importing another module's store.

One dispatcher owns each producer outbox, serializing batches of up to 32. Each
publication has a cooperative two-second budget. Confirmed success marks delivered;
failure increments an attempt count. At five failures the retained record becomes
exhausted and producer readiness fails. Delivered and exhausted records remain
retained and count toward the existing 10000-record memory cap. Parent cancellation
leaves pending state without consuming an attempt. A lost acknowledgment can
replay the same event after a consumer already committed.

Owned workers poll every 100 ms. Process assembly registers capabilities and
subscriptions, freezes them and verifies required providers before workers/HTTP
admission. After HTTP drain, it fences all workers, joins them, then closes
Identity's crypto resource. Worker close retains its join handle across await,
so canceling close cannot detach it. Drop requests cancellation/abort as a
fallback; normal shutdown explicitly joins. Shutdown does not promise an outbox
flush. Stopped/panicked workers or exhausted delivery fail readiness; live stays
separate. The server supports selected probes, not a general module lifecycle registry.

Audit owns Evidence/Entry, named Record/List/ListPrincipal handlers, ports and
its own memory store. Receiving adapters admit Account created/renamed v1 and
Identity registered/issued/renewed/revoked v1. They check producer/scope/version
and payload agreement, preserve ordered duplicates/case folding/null no-op,
reject unknown fields and map only selected evidence. The
[recovery extension](rust-recovery.md) adds email.verified/password.changed/
password.recovered v1 and session.revoked.v2 with fifteen shared admission cases.
[Files](rust-files.md) adds its three v1 lifecycle names with thirteen shared
admission cases, optional file_id and unchanged older fingerprints.

Audit atomically appends evidence and an inbox marker keyed by consumer/event ID.
An identical replay succeeds without appending or changing first recording time;
different evidence under that identity fails. At most 10000 entries are admitted.
Audit's own append sequence is global to that store; filtered pages may have gaps
and there is no global source-event ordering or exactly-once execution promise.

Audit asks its consumer-owned AccountAccess port through `account.access.v1`
before loading account evidence. Account authorizes current ownership. Unknown
account/non-owner is an admitted false, mapped to Audit 403. Missing/malformed
responses, provider failure and timeout stay failures. The shared Principal
package supplies edge credential evidence/resolution to both HTTP modules;
Account/Audit import no foreign implementation. Default deny still denies all
activity reads. Credential v2 remains bound to `api`; AccountAccess uses `audit`.

GET/HEAD `/v1/accounts/{id}/audit` and `/v1/principals/me/audit` return selected
entries and nullable next_cursor. Personal scope comes from the trusted principal.
Limits default to 50, range 1..100. Go Audit's Atoi-compatible `01`/`+1` forms are
retained; Account listing's canonical integer rule differs. Cursors bind account
or principal and an exclusive positive JSON-safe append sequence; they do not
authorize reads. Unknown/repeated/invalid query fields and foreign cursors fail.
HTTP writes return after producer commit without waiting for Audit; initial reads
may be empty and catch up. There is no HTTP event injection or replay endpoint.

Evidence: 33 shared event cases, seven AccountAccess response cases, 24 shared
HTTP cases and real Go/Rust processes for Account metadata/versions, repeatable
bootstrap, paginated scope isolation and Identity registration/issuance/renewal/
revocation evidence. Rust additionally tests atomic mapping failures, independent
delivery, timeout/cancellation, exhausted readiness, concurrent inbox replay,
lost acknowledgment and cancellation-safe worker joining. Run
`make backend-audit-parity-test`, `make rust-check rust-test` and existing parity
targets. Private-input logs, SIGTERM and listener closure are checked per process.

In the memory profile all state/delivery/inbox/evidence disappears on exit.
[PostgreSQL migrations/restart recovery](rust-postgres.md) have separate acceptance.
Broker transport, retention/admin replay, complete
broadcast/WebSockets and native/CLI Rust acceptance
remain separate. Read [the inventory](../../backend/rust/PARITY.md).
