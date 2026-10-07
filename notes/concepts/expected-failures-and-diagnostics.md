# Expected failures and private diagnostics

An expected failure tells the caller what happened, while an original exception
helps a developer investigate why; they have different ownership and lifetimes.

## Origin and evidence

The first native kernel slice follows Bento's value-based service convention.
Reviewed 2026-10-08 through source inspection, shared fixtures, and native tests.
The [kernel contract](../../contracts/behavior/kernel.md) records the selected
behavior. The native module walkthroughs link actual source and checks.

## What and why

A result has one active branch: a successful value or a classified expected
failure. A successful optional absence is different from `not_found`: the
operation contract decides whether absence is ordinary or a refusal.

A broad kind supports shared behavior, while a code carries a specific reason.
For example, `conflict` can select a conflict treatment, and
`item.version_conflict` can select deliberate explanation or recovery behavior.
Neither label chooses retry safety by itself.

The failure carries selected metadata and variant-specific payloads. It does
not retain an arbitrary exception, stack, HTTP object, or diagnostic dictionary.
An adapter reports an original defect separately. Catching every exception as
`unavailable` would conceal a bug and could trigger an inappropriate retry.

Public projection redacts internal message/code and preserves request IDs so a
reported problem can still be investigated. Non-internal text remains the
caller's responsibility. Projection is a deliberate boundary operation; it
does not infer whether arbitrary supplied strings are safe.

## Example

| Operation outcome | Meaning |
| --- | --- |
| Success with no saved selection | Normal absence under that query's contract |
| Invalid with an email field message | Expected input refusal the caller can explain |
| Rate limited with 1500 ms | A supplied minimum wait; retry policy still owns the decision |
| Internal with a private detail | Public projection replaces the detail and removes the code |
| Unexpected exception or task cancellation | Normal language control flow, handled by its owning boundary |

## Gotchas

Swift's standard Result mapping takes non-throwing callbacks. Kotlin's mapping
callbacks may throw; their exceptions escape unchanged. These language choices
implement the same result branch semantics without promising identical syntax.

The `canceled` category does not authorize an adapter to swallow structured
cancellation. A boundary that intentionally returns it must say so. Current
kernel checks cover values and synchronous mapping; later async adapters need
task and coroutine cancellation scenarios.

## Used in and related

Use these distinctions when specifying service, repository, session, and sync
boundaries. Read the [Swift kernel walkthrough](../../frontend/swift/notes/modules/packages/FoundryKernel/README.md)
or [Kotlin kernel walkthrough](../../frontend/kotlin/notes/modules/project/core/kernel/README.md)
to connect the reasoning to native types.
