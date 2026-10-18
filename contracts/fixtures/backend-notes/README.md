# Backend Notes conformance

The 47 HTTP cases cover the current Go Notes v1 boundary and Rust's independent
implementation. `scripts/notes_parity.py` runs them against fresh owned processes,
alongside real twelve-way matching creates, competing CAS commands, historical
replay after deletion, pagination and account/session isolation.

Titles reuse `../notes/create-titles.json`: eleven scalar-count/admission cases.
`fingerprints.json` fixes four `notes.write.v1` command digests with exact field
order, empty create target, and Go encoding/json escaping. Cases include literal
backslashes, HTML characters, JavaScript separators, NUL, Unicode and preserved
whitespace. Go marshals the versioned shape independently; Rust's actual adapter
hashes it. The PostgreSQL runner additionally replays actual Go/Rust stored
receipts across process handoff, rather than relying on fixtures alone.

Read [Rust Notes behavior](../../behavior/rust-notes.md) and the
[shared contract](../../behavior/remote-notes.md). This corpus does not establish
malformed Unicode replacement equivalence, full routing/network parity,
WebSockets, automatic sync or generated/native-connected Rust readiness.
