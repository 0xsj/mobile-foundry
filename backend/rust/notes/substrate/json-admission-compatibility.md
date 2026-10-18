# JSON admission compatibility

Claim: sharing a bounded JSON frame contract still requires endpoint-specific
decoding cases when two languages use different DTO libraries.

Identity follow-up, 2026-10-18: credential endpoints now use the shared bounded
byte reader with their own ordered Serde visitor. It preserves Go string null
no-op, case folding and duplicate order, refusing an earlier wrong type. Seventeen
shared live wire cases pass. The generic Value-based helper retains the limits
below; malformed Unicode repair remains outside the credential compatibility
claim. Read [Identity](../modules/internal/identity/README.md).

Origin/evidence: 2026-10-18 source inspection and execution with Axum 0.8.9,
serde_json 1.0.154, serde_ignored 0.1.14 and mime 0.3.17 on Rust 1.86.
Twenty-six common JSON/media cases and twenty-seven version/key cases pass
against Go and Rust's actual helpers. Documented differences below are not
claims that the helper corpus covers every DTO.

Primary references: [Axum bounded body collection](https://docs.rs/axum/latest/axum/body/fn.to_bytes.html),
[serde_ignored](https://docs.rs/serde_ignored/0.1.14/serde_ignored/),
[Serde container attributes](https://serde.rs/container-attrs.html) and
[Go JSON decoding](https://pkg.go.dev/encoding/json#Unmarshal).

The reader limits actual body bytes to 16384, including whitespace, rather than
trusting Content-Length. Axum exposes a limit-specific source, allowing 413 to
remain distinct from a broken body stream's invalid-JSON refusal. The owned
deadline turns a stalled stream into timeout; a virtual-time test observes that
path without waiting fifteen real seconds. No automatic read retry exists.

serde_json first normalizes the bounded object into Value. This retains the last
member for duplicate keys; direct Serde struct decoding would reject duplicate
fields. serde_ignored then reports fields the target DTO discards, including
nested paths. The helper rejects any reported field even if the DTO author
omitted deny_unknown_fields. Open map/Value fields deliberately admit their own
keys and need an endpoint schema when arbitrary keys are inappropriate.

The optional string/nested bool fixture uses Option fields in Rust and pointers
in Go, so absent/null normalize identically. That choice is explicit and is not
the same as every Go string/bool DTO. Go can reject an earlier wrongly typed
duplicate before a later valid value; normalization in Rust loses that earlier
value. Repeated nested structs can merge in Go, while Value replaces the earlier
object. Go field-name folding, invalid Unicode, numeric conversion and MIME
parameter extensions also differ. Endpoint authors must choose/test those cases
against the actual Go DTO before claiming replacement compatibility.

Gotchas: bounded bytes are not an authentication check, domain policy or complete
CPU/network budget. The server still lacks Go's full read/write/idle policy.
Used in [the HTTP walkthrough](../modules/pkg/httpx/README.md) and
[parity inventory](../../PARITY.md). Related:
[wire/domain split](../patterns/wire-admission-before-domain.md). Next: should a
new endpoint retain Go's ambiguous duplicate behavior or version a stricter rule?
