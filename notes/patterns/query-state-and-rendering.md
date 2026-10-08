# Query state and rendering ownership

A reusable query snapshot and a renderer can be shared without sharing the
runtime that owns requests.

## Origin and evidence

The 2026-10-08 extraction followed a working provider-independent list example.
Source inspection, shared state fixtures, native state-owner regressions, Android
instrumentation, and iOS simulator interactions support the separation. The
[contract](../../contracts/behavior/query-ui.md) owns the precise behavior.

## What and why

State answers what can be shown: idle, loading with an optional previous value,
loaded, or failed with an optional previous value. The owner answers whether an
asynchronous result may still be published. The renderer answers how that state
appears and emits user actions. These are separate responsibilities even when
one feature initially implements all three.

A successful empty value must remain distinguishable from no value. After an
empty success, refresh shows both refreshing and empty presentation. Failure
keeps the empty snapshot; cancel restores the successful empty state.

The list example and a scalar workspace gallery now use the same value state
and presentation. Their execution differs: the list owns asynchronous work,
while the gallery selects states manually. That is evidence for extracting
state and rendering, rather than an all-purpose request controller.

## Gotchas and use

Pure `settled` transforms an already admitted result; it has no generation,
task, or cancellation check. A caller that settles obsolete work still has a
race. Keep those checks in the feature owner until another real feature proves
the same orchestration requirements.

An immutable wrapper does not freeze its payload. Callers must supply stable
snapshots. A retained value is screen state, not a shared cache: it has no query
key, cross-screen invalidation, or freshness policy. Emptiness is also a domain
decision supplied to presentation, not a generic list assumption.

Use this pattern for value-driven native screens whose data source or lifetime
can vary. Introduce cache, mutation, session, and synchronization behavior with
their own contracts and examples.

## Related and next questions

Read [provider seams](transport-service-and-screen.md#provider-seams-and-query-state)
and [owned cancellation](../concepts/deadlines-and-owned-cancellation.md).
Which second asynchronous feature would justify shared request orchestration?
Which mutation should invalidate a query, and who owns that decision?
