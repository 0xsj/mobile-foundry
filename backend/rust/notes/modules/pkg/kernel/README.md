# Kernel failures

Claim: an owned failure retains private diagnostic meaning while exporting only
the vocabulary deliberately admitted to clients.

Admission follow-up, 2026-10-18: read
[Clock](../../../../pkg/kernel/src/clock.rs),
[Id/generation](../../../../pkg/kernel/src/id.rs) and
[shared identifier tests](../../../../pkg/kernel/tests/ids.rs) after failures.
Clock supplies swappable business wall time; request timing remains monotonic.
Id validates canonical nonzero form, and RandomIds uses fallible OS entropy
before setting v4 bits. A generation error retains its source and has no
predictable fallback. Fixed-clock, deterministic entropy/refusal and eleven
shared identifier cases execute. Identity and Account now consume Clock/Generator
in real business handlers, independently of request-ID allocation.
See [newtype mechanics](../../../language/newtypes-and-transport-errors.md).

Account follow-up: [mechanical context](../../../../pkg/kernel/src/context.rs)
now supplies shared Scope/Metadata and PortFuture. HTTP and capability budgets
adapt cancellation/correlation through it, while authenticated principal/session
evidence stays in consumer-owned application ports. No business authority enters
the shared context.

Origin/evidence: 2026-10-18 implementation and the shared projection corpus.
Read [the source](../../../../pkg/kernel/src/lib.rs), then the
[HTTP projection test](../../../../pkg/httpx/tests/boundary.rs) and
[shared expectations](../../../../../../contracts/fixtures/backend-runtime/failures.json).

Kind is the common ten-case enum. Failure's fields are private; builder methods
own their input. A private source is shared through Arc for safe cloning. The
public projection copies code/message and invalid-only fields; internal always
becomes the generic message without code/fields. Failure itself is not a wire
DTO and Debug/Display expose no diagnostic content.

For example, the internal fixture deliberately supplies a safe-looking public
message and fields; the projected result still removes both. A forbidden fixture
retains its explicit message/code but removes validation fields. Eleven cases
run through both language projectors, covering source retention and private
content absence in Rust's body/default formatting.

Gotchas: callers must supply safe public text; source traversal is private and
can reveal causes if logged explicitly. Joined conditions and Go-style error
identity are not implemented. Related:
[Result/ownership](../../../language/results-and-owned-async-ports.md) and
[HTTP](../httpx/README.md). Next: which future conditions need an owned module
type rather than a new shared failure kind?
