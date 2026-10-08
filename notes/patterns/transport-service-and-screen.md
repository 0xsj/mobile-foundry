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

## Provider seams and query state

The 2026-10-08 boundary review distinguishes transport injection from provider
substitution. Both native catalogs inject wire responses, but their health
screens construct the concrete HTTP client and health service. The
[Swift catalog walkthrough](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md)
and [Android catalog walkthrough](../../frontend/kotlin/notes/modules/project/app/README.md)
link the inspected sources. There is no domain service protocol/interface or
separate health store/view model yet. The Android starter's repository interface
covers its generated greeting example only.

Bento's web query bridge adapts service Results to query-library errors, while
the query client owns caching and retry behavior. Those responsibilities are
separate from the service contract that makes implementations interchangeable.
Adding query state alone does not create a provider seam.

The notes/query slice implements screen → store/view model → domain port →
selected adapter. A Swift protocol or Kotlin interface describes domain
operations returning AppResult; app composition currently chooses memory or
HTTP. Future provider SDK adapters can satisfy the same port after conformance
checks. Inputs and outputs should use domain values
and only the operation context callers actually need. HTTP request options and
SDK models stay inside adapters. Each adapter preserves cancellation and maps
expected dependency failures to the agreed failure vocabulary.

The store/view model owns loading, content, failure, refresh and obsolete-result
handling independently of the selected provider. Shared caching, deduplication
and invalidation can grow when concrete callers need them; a local observable
repository is a further boundary when persistence and synchronization arrive.

The [notes/query contract](../../contracts/behavior/notes-query.md) now has the
first proof: the same feature-state scenarios run against memory and HTTP
implementations without editing the screen. The service suites consume 12 shared
admission cases; state tests additionally exercise retention, recovery, late
results, cancellation and diagnostics. Both native catalogs expose provider and
scenario selection. HTTP catalog responses are injected, so this proves adapter
substitution, not production connectivity. See the linked catalog walkthroughs
for actual device evidence and native lifetime differences.

This feature state is smaller than Bento's query client: no shared query keys,
cache freshness, deduplication, mutation invalidation or durable storage. Native
state owners consume AppResult directly; they need no Result-to-query-exception
bridge. Supabase/Firebase adapters still need conformance checks for their actual
behavior; an interface cannot supply missing backend idempotency, authorization
or sync guarantees. The existing health example remains a wire-pipeline catalog.

The subsequent [query/UI extraction](query-state-and-rendering.md) shares generic
snapshots and native rendering across the list and a scalar gallery. It leaves
request lifetime and result admission with each feature owner.

## Related

[Owned cancellation](../concepts/deadlines-and-owned-cancellation.md) and [Shared fixtures and native adapters](../techniques/shared-fixtures-and-native-adapters.md).
