# Go JSON encoding inside a stable command fingerprint

Claim: two JSON documents can mean the same thing while producing different
command hashes, so a versioned fingerprint needs an explicit byte convention.

Origin/evidence: 2026-10-18 Go's Notes v1 encoding inspected locally, four shared
digest vectors executed in Go/Rust and actual PostgreSQL receipt replay in both
directions. Rust uses serde_json 1.0.154; Go uses encoding/json's default Marshal.

Notes v1 hashes a fixed prefix plus compact JSON with a fixed field order. Create
uses an empty note target because its generated ID is output. Go's default JSON
encoding escapes HTML characters (`<`, `>`, `&`) and the U+2028/U+2029 separator
scalars. Serde's ordinary serializer does not use all those default escapes.
Plain serde serialization would therefore break replay for otherwise valid titles.

Example (Conceptual): a literal less-than character encodes as `\u003c` in Go's
default JSON. A title containing the literal six characters backslash-u-0-0-3-c
must remain a different input. Start from correct JSON string serialization, then
apply the required scalar escapes; do not replace already escaped text or
normalize whitespace/Unicode. The fixed shape also includes numeric expected
version and an explicit empty create target.

The HTTP response only needs semantic JSON compatibility. The fingerprint needs
byte compatibility. Persisted receipt JSON is decoded/validated rather than
hashed again, so its harmless member order/escaping need not match. Keeping this
adapter-specific encoding out of the domain lets typed domain values remain
independent of a serializer.

Gotchas: struct field renaming, default omission, field order, whitespace or a
serializer upgrade can change hashes. Preserve old version bytes and introduce
a deliberately versioned migration when the protocol changes. bytea storage also
avoids PostgreSQL text/jsonb's inability to preserve embedded NUL in these values.
Used in [Notes adapter](../modules/internal/notes/README.md). Related:
[JSON admission limits](json-admission-compatibility.md) and
[historical receipts](../patterns/historical-receipts-before-current-state.md).
