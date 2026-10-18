# Wire admission before the domain

Claim: transport admission should establish bounded, explicit input shape before
domain constructors decide whether those admitted values make business sense.

Origin/evidence: 2026-10-18 shared Go/Rust JSON/header fixtures, including exact
size boundaries, nested unknown fields and typed media/precondition refusals.

The body reader first checks media selection, then bounds actual bytes under the
request deadline. JSON must be a single object with no trailing value. Typed
decoding observes ignored fields, turning otherwise silent unknown-field handling
into a public refusal. The DTO can then be passed to its domain constructor;
the transport does not interpret names, passwords, ownership or write authority.

For example, missing If-Match produces 428 before there is a version value. A
valid quoted version produces an immutable wrapper, but a future repository must
still compare that value atomically against current state. An admitted
Idempotency-Key similarly carries intent metadata without implementing receipts
or making a repeated command safe. Errors stay owned by their responsible layer.

Gotchas: media/JSON libraries differ at edge cases; a shared framing helper does
not prove every endpoint DTO equivalent. Read
[the decoder comparison](../substrate/json-admission-compatibility.md) before
adding Identity. Encoding a complete response before returning it avoids a
half-written success when serialization fails; streaming needs another policy.

Used in [HTTP admission](../modules/pkg/httpx/README.md); Health already consumes
the common response encoder. Related:
[newtypes/status ownership](../language/newtypes-and-transport-errors.md) and
[behavioral parity](../concepts/behavioral-parity.md). Next: what should a password
DTO admit before the Identity domain validates its policy?
