# Explicit boolean presence at a JSON boundary

Claim: a pointer distinguishes an explicitly supplied false from absent input,
while exact field/duplicate admission remains a separate parser decision.

Origin: duplicate-risk acknowledgement in the Go recovery slice, 2026-10-17.
The repository uses encoding/json on Go 1.27.1. Its
[Unmarshal documentation](https://pkg.go.dev/encoding/json#Unmarshal) describes
pointer/null handling; native Go HTTP checks observe the request cases here.

## What and why

A plain bool starts false, so a missing field could look like an explicit choice.
A freshly initialized *bool starts nil. Decoding false creates a non-nil pointer
to false; missing/null remain nil and can be rejected. Dereference only after the
presence check. Missing and null intentionally share the same refusal here; a
different contract could need another representation to distinguish them.

Excerpt from the recovery transport's input/admission shape:

```go
// Excerpt: a request-owned DTO; its custom decoder also checks exact member names.
type replacementInput struct {
    ID string
    Accept *bool
}
// After decoding: if body.Accept == nil, reject before using *body.Accept.
```

## Gotchas and actual use

Pointer presence alone does not reject duplicate JSON members or case variants.
The legacy struct decoder can match names without case and accept later duplicate
values. The recovery input therefore scans its flat object's tokens, tracks seen
keys and admits only the two exact names before decoding their values. Shared
HTTP admission still bounds the body, requires JSON media type and rejects extra
documents. This does not migrate the repository's other DTOs to a new JSON API.

[Transport and checks](../modules/internal/notifications/recovery.md) link that
implementation. Tests reject missing/null/duplicate/case-variant acknowledgements,
admit false for known-unsent work and require true for uncertain sources. A boolean
acknowledgement is caller intent, not proof about a remote provider's outcome.
