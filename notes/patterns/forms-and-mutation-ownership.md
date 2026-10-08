# Forms and mutation ownership

A draft, an admitted command, and a confirmed receipt have different lifetimes;
keeping them separate makes failure recovery and duplicate prevention explicit.

## Origin and evidence

The 2026-10-08 Create note slice follows the [forms contract](../../contracts/behavior/forms-mutations.md).
Trace the actual implementation through the [Swift catalog](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md)
and [Kotlin app](../../frontend/kotlin/notes/modules/project/app/README.md) walkthroughs.
Shared command/response fixtures and native state-owner tests establish admission
and publication behavior. Device controls have separate framework evidence.

## What and why

The draft is editable raw text. Blur or submit marks a field touched; the
command factory then produces an expected invalid failure or a value that a
provider can accept. The factory preserves input rather than silently trimming.
The create limit counts Unicode code points on both platforms; grapheme clusters
such as an accented letter can contain multiple code points. Product copy and
future localization should account for that distinction.

MutationState carries Idle, Submitting, Succeeded(receipt), or Failed(failure).
It has no earlier-success snapshot: a new attempt must not inherit confirmation
from a different write. The feature separately owns draft, touched state,
execution, generation, and field mapping. UI controls receive values/callbacks.
The write port accepts only the admitted command, leaving memory, HTTP, or a
future SDK adapter interchangeable without putting provider flags in the form.

For example, submit an empty title: show the title error, focus the field, and
invoke no service. Enter a valid title: mark Submitting synchronously before
launching work. A second button tap or keyboard action observes busy and cannot
dispatch another write. A server refusal keeps the draft; recognized title
errors appear inline, while the summary still covers unknown server field keys.
Editing clears the server receipt/refusal and revalidates a touched field.
Confirmation locks the draft until New note explicitly resets it.

## Delivery and lifetime gotchas

One active invocation is a local concurrency rule. It supplies no server
idempotency, persistence, or protection against a repeat after a lost response.
Timeout, cancellation, malformed confirmation, and unavailable service can leave
commit status unknown. The UI says to check notes before submitting again;
there is no automatic retry or Cancel write button implying rollback.

On screen exit, advance generation before requesting cancellation. A dependency
may ignore cancellation or complete just as the screen exits. Generation controls
publication; cancellation only asks execution to stop. Current defects retain
their original identity at the diagnostic callback, while UI uses public failure
copy. Obsolete completions cannot change state or produce current diagnostics.

The memory provider atomically appends and can list its own snapshots. Catalog
read/write examples currently compose separate instances, and injected HTTP
does not contact a backend. A success receipt therefore does not imply that the
other gallery updated. Connecting reads, durable drafts, reconciliation and
query invalidation requires a later data-ownership slice.

## Used in and next questions

Use this arrangement for a small explicit command form before extracting a
generic form engine. The title-only example does not establish multi-field
cross-validation, asynchronous validation, file upload or optimistic rollback.
Which operation identity would permit safe replay? What local record survives
process death? Which repository owns the confirmed change and read observation?

Related: [query ownership](query-state-and-rendering.md),
[service seams](transport-service-and-screen.md),
[cancellation](../concepts/deadlines-and-owned-cancellation.md), and
[private diagnostics](../concepts/expected-failures-and-diagnostics.md).
