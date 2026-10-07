# Transport, service, and screen

Separate wire admission, domain admission, and presentation so each can be tested with its own evidence.

## Origin

The 2026-10-08 HTTP and health slice follows Bento's web HTTP port, problem decoder,
and health service. Source inspection and shared native fixtures establish the
implemented boundaries. [HTTP contract](../../contracts/behavior/http.md) remains the promised behavior.

## What and why

Transport receives a prepared request and returns owned bytes and HTTP metadata.
The client admits JSON and selects expected failure values. A service chooses an
endpoint and validates successful domain data. The screen renders that domain
value or deliberately selected public failure copy.

Using one generic JSON decoder for a service would admit a syntactically valid
array or an object missing required fields. Validation at the service keeps
wire shape changes from silently entering feature state. Putting status mapping
in each service would instead duplicate failure policy.

## Example

HTTP 200 with `{ "status": "ok", "extra": 1 }` is valid JSON. Health admission
selects only status and response metadata. HTTP 200 with `[]` is valid JSON too,
but health admission refuses it with `http.invalid_response`. HTTP 503 maps to
unavailable before domain validation and passes through the service unchanged.

## Gotchas

A generic entity's ETag is a version token, not a domain field. Services decide
whether to expose it. Passing If-Match or Idempotency-Key does not implement
backend conflict handling or deduplication. Repositories, session renewal, and
retry orchestration will sit above these request boundaries.

## Used in

Small endpoint services and feature-specific network boundaries. The catalogs
inject a wire transport and still execute the real client and service pipeline.

## Related

[Owned cancellation](../concepts/deadlines-and-owned-cancellation.md) and [Shared fixtures and native adapters](../techniques/shared-fixtures-and-native-adapters.md).
