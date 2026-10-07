# Native HTTP and health service behavior

Status: Established for the first transport slice, 2026-10-08.

HTTP owns one application-level attempt; services own endpoint paths and
successful domain validation. A caller receives an entity with parsed JSON
and response metadata, or a kernel failure. A health service projects that
entity into a narrow Health value. UI consumes the service rather than wire JSON.

## Request admission

The client factory accepts an absolute HTTP(S) base with optional path prefix,
without user credentials, query, or fragment. The default budget is 15000 ms;
budgets are integers from 0 through 2147483647. Invalid configuration is
`internal` with `http.invalid_configuration`; invalid per-call timing uses
`http.invalid_timeout`.

Paths are API-relative, optionally with one leading slash. They cannot contain
an absolute URL, authority, whitespace at their edges, query, fragment,
backslash, control character, malformed percent escape, traversal segment,
or encoded slash/backslash. Prepared requests stay within the base prefix.
Query items are ordered string pairs; repeated names are preserved.

Methods are GET, HEAD, POST, PUT, PATCH, and DELETE. Optional bodies must be
JSON objects; GET and HEAD cannot have bodies. Headers use HTTP token names
and values without prohibited controls or non-Latin-1 characters; horizontal
tab is permitted, matching Bento. Accept is
`application/json, application/problem+json`; Content-Type is set to JSON only
when a body exists. Options explicitly supply correlation ID, If-Match, and
Idempotency-Key. Invalid preparation is `internal/http.invalid_request` and
must not call the transport. No session or credential store is owned here.

## Exchange and JSON

The budget covers preparation, exchange, full body reading, and decoding, with
cancellation checks before sending and after suspension/decoding. Zero budget
returns timeout without sending. Both native transports cooperate with
cancellation. Swift's structured deadline scope waits for child cleanup;
injected transports must cooperate, so it does not promise prompt settlement
for an adapter that ignores cancellation.

There are no application retries, redirects, response cache, or credential
renewal. OkHttp connection retries are disabled. Underlying networking may have
platform connection mechanics; this is not a claim about physical packet count.

For 2xx: HEAD, 204, 205, empty/whitespace bodies, and JSON null produce a null
body. Other bodies must be valid JSON; malformed success is
`internal/http.invalid_response`. JSON values support objects, arrays, strings,
booleans, finite double-range numbers, and null. Successful response metadata
retains status, ETag, request ID, and correlation ID.

A broken successful body is `unavailable`. A broken or malformed error body
retains classification from the received HTTP status and headers. HTTP headers
are case-insensitive. Request ID header wins over problem `request_id`; a
caller correlation ID wins over a response correlation header. Kernel metadata
does not acquire an HTTP-status field; status stays transport-owned.

## Failure decoding

Match Bento's `problem.ts`: recognized problem `kind` wins; unknown/missing kind
falls back to the actual status. Detail, then title, then `The request failed.`
supplies the message. Problem codes/IDs and response IDs admit nonblank strings only; an explicitly
supplied caller correlation ID retains precedence. Problem
`type` is a URI, not a code. Invalid fields select string entries only. Extra
properties and diagnostics are never copied into failures.

| HTTP status | Fallback kind |
| --- | --- |
| 400, 413, 415, 422, 428 | invalid |
| 401 | unauthenticated |
| 403 | forbidden |
| 404 | not_found |
| 408, 504 | timeout |
| 409, 412 | conflict |
| 429 | rate_limited |
| 502, 503 | unavailable |
| Other non-2xx, including redirects and 500 | internal |

Retry-After accepts unsigned integer seconds or the three HTTP-date forms.
Past dates clamp to zero. Invalid/overflow timing is absent. To match Bento's
numeric interoperability, decoded delays cannot exceed 9007199254740991 ms.
Date parsing uses an injected current time for deterministic tests.

## Dependency errors and cancellation

Known transport connection failures become unavailable; known connection/read
timeouts become timeout. Original dependency errors are sent to an injected
diagnostic callback, separate from public values. Unexpected errors propagate without application classification or replacement.
Coroutine stack-trace recovery may copy an exception and retain its original
as cause; this runtime behavior is separate from the adapter policy. Serialization and domain logic are outside the network
catch. Error-body read failure preserves received status classification.

Parent/task cancellation throws CancellationError or CancellationException and
cancels the native request. A deadline is timeout data and cancels its request.
Neither cancellation nor timeout implies a remote write was undone.

## Health and catalogs

Health live/ready use `health/live` and `health/ready`, equivalent to Bento.
A successful root object must have string `status` equal to `ok`; extra fields
are discarded. Null, arrays, missing or wrong status are
`internal/http.invalid_response`. Expected HTTP failures propagate unchanged;
original unexpected exceptions and structured cancellation propagate.

The SwiftUI and Compose examples use injected responses for healthy,
unavailable, malformed, validation, throttling, and timeout scenarios. They
show loading, healthy, or deliberate failure copy. Changing a scenario or
leaving the screen cancels owned work; canceled work cannot replace a newer
state. Catalogs require no running backend.

## Verification and scope

Shared JSON fixture files under `contracts/fixtures/http` cover exchange,
status, body, metadata, health admission, path admission, and retry timing.
Native tests separately check preparation, deadlines, caller cancellation,
body read errors, defect identity, and actual networking adapters.

Uploads/streams, generated wire declarations, maintenance status, session,
credential renewal, retry/idempotency policy, and provider SDKs are deferred.
Idempotency and version headers supply metadata; this slice does not implement
server deduplication or conflict resolution.
