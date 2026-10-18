# Notifications conformance corpus

Twenty-two event cases admit only the curated upload-completion contract and
check producer/name/version/scope, unknown/private data, duplicate/null behavior
and malformed input. Four fixed fingerprints preserve notifications.files.v1's
NUL-separated source input and canonical microsecond times, excluding delivery
time and settings. Forty-three HTTP cases cover read/list/preference admission,
pointer-bool DTOs, query/header/body refusal, filters and limits.

[The runner](../../../scripts/notifications_parity.py) exercises real Go/Rust APIs:
upload completion through the event bus, first-read preservation, preferences,
account/principal isolation, newest-arrival paging, cursor binding and private
logs. Rust store conformance separately covers concurrent replay/snapshots and
suppression after preference changes. No public injection endpoint is added.

[Disposable PostgreSQL](../../../scripts/postgres_parity.py) adds transaction
faults, six-database selection, killed pending delivery and Go/Rust handoff of
source markers, suppressed decisions, first-read state and preferences.
Installations now has [separate acceptance](../backend-installations/README.md);
copied push SQL alone establishes no sender/jobs capability.

Read [shared behavior](../../behavior/notifications.md) and
[Rust scope/evidence](../../behavior/rust-notifications.md). Malformed Unicode,
uncaptured MIME/router/runtime differences and generated native/CLI Rust products
remain outside this corpus.

