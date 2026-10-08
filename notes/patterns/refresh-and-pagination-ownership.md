# Refresh and pagination ownership

Claim: refresh gestures and page footers project operation lifetime; the feature
owns the records, concurrency and result-admission policy behind them.

## Origin and evidence

Added 2026-10-09 while composing the seventh native UI batch. The Activity
catalog uses a local fixture, native List/LazyColumn and a 450 ms pause. Source
inspection establishes separation; native module walkthroughs link executed
checks. This is a reusable ownership pattern, not evidence of a working remote
feed, durable cache or sync protocol.

## What and why

A refresh gesture requests a replacement snapshot. A page action requests more
records without hiding the current snapshot. Treating both as one global loading
screen loses useful content and makes page failures look like initial failures.
The explicit footer has idle, loading, failed and exhausted presentations; its
callback only asks the host for work. It never starts a page on appearance.

The refresh spinner should follow the operation. Swift's native refresh awaits
the host's async action. Kotlin's native refresh reads a host-owned busy flag.
Those signatures differ while expressing the same lifetime. A detached task in
the Swift callback would let it return before the work completes; an uncleared
Kotlin busy flag would leave the spinner active after cancellation.

The fixture serializes refresh/page requests. Failed pages preserve rows and
expanded IDs. Refresh replaces the first page, retains expansion only for IDs
still present and resets pagination. These are explicit example policies; a real
feature may merge new rows or preserve a longer cached window instead.

Example: three rows → Fail next page → Load more → failed footer with three rows
→ Retry → six rows → Load more → nine rows and exhausted footer. Refresh returns
to three rows without collapsing an expanded surviving record.

## Gotchas and alternatives

- Row positions change when records arrive. Key both native rows and expansion
  state by stable identity. A formatted title or timestamp is not an identity.
- Native lazy lists do not create every offscreen row. Android tests must scroll
  the list to a matching item before locating its descendants.
- Hoisting values above a route retains them across Back; it does not make them
  durable or process-restorable. The Activity fixture is intentionally ephemeral.
- Leaving a route should end its work. Compose's destination effect is cancelled
  on removal; Swift view tasks are cancelled and a revision guard also rejects
  late native refresh results. Cancellation must not manufacture successful rows.
- Refresh containers need supported scrolling content and bounded layout. Do not
  nest another unbounded vertical lazy list inside a gallery scroller.
- Avatar groups summarize all members, including visually hidden overflow.
  Timeline lines are decorative. Long-copy actions remain independent controls.

## Used in and related

Read [behavior](../../contracts/behavior/ui-components.md#activity-and-paged-collections),
[usage](../../docs/blueprints/ui-components.md#activity-feeds-and-pagination),
[Swift native mechanics](../../frontend/swift/notes/substrate/swiftui-refresh-and-lazy-activity.md)
and [Compose native mechanics](../../frontend/kotlin/notes/substrate/compose-refresh-and-lazy-activity.md).
The existing [provider seam](transport-service-and-screen.md#provider-seams-and-query-state)
and [query rendering](query-state-and-rendering.md) remain separate; a future
remote feed should enter through an app/domain port rather than the UI wrapper.
Next: what cursor/merge contract and cached-window policy does a real feed need?
