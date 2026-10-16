# Local versioned capability invocation

A local call can preserve a transport boundary's validation, caller policy,
deadline and public failure semantics while both modules share a process.

Origin: 2026-10-13, implemented after the Bento reference investigation.
Read [runtime](../../../../pkg/capability/capability.go),
[tests](../../../../pkg/capability/capability_test.go), then
[Audit's client](../../../../internal/audit/infra/capabilities/account.go).

The registry holds named provisions; composition binds an invoker to a module
identity. Providers declare allowed callers. Register all providers before
resolving requirements, then freeze. Request/response JSON admission prevents
passing another module's implementation values directly through a call.

Conceptual flow: consumer port → client → admitted JSON → provider handler →
admitted JSON → consumer-owned value. Local failure projection drops private
causes/diagnostics, so consumers branch on admitted categories/contracts rather
than dependency implementation pointers. Cancellation preserves context identity.
This differs intentionally from ordinary internal error wrapping.

The two-second budget is cooperative and bounded by the caller. A handler must
honor context; a late successful result is refused. No goroutine is abandoned to
fake a hard timeout. Each object is bounded to 16 KiB; required fields and allowed
business values remain the provider/client's responsibility. Go's duplicate-key
behavior matches the current HTTP decoder.

`make check test` passes caller/refusal, negative answers, malformed objects,
response shape/size, cancellation/deadline and private failure projection.
Remote invocation and service authentication remain unimplemented; local tests
do not prove parity with an absent adapter. Next: exercise the same contract
against a real remote adapter before allowing process extraction.

Related: [boundary policy](../../../../../../contracts/behavior/module-boundaries.md),
[Audit](../../internal/audit/README.md),
[assembly](../module/README.md) and
[ports/composition](../../../patterns/ports-and-module-composition.md).
