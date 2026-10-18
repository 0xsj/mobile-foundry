# Files conformance corpus

Twenty-four specifications check normalized names, Unicode/control/path bounds,
declared types, size limits and lowercase digests. Four fixed command fingerprints
preserve Go's exact JSON field order/escaping and files.command.v1 prefix.
Thirteen Files events check scope, subject, version, vocabulary and private-data
refusal. Forty HTTP cases cover admission and expected failures; the runner
also sends an oversized binary body.

[The runner](../../../scripts/files_parity.py) uses real owned Go/Rust APIs,
including repeated immutable bytes, concurrent receipt replay, guarded lifecycle,
binary/HEAD responses, pagination, authorization and causal Audit. Rust-only
checks exercise atomic failure, cleanup retry, corruption and owned I/O lifetime.
[Disposable PostgreSQL](../../../scripts/postgres_parity.py) verifies rollback,
deferred receipt finalization and retained directory/metadata across process kill
and Go/Rust handoff. These checks use no cloud bucket or real personal media.

Read [shared behavior](../../behavior/files-media.md) and
[Rust scope/limits](../../behavior/rust-files.md). Native/CLI Rust product acceptance
and uncaptured parser/network edge cases remain separate.

