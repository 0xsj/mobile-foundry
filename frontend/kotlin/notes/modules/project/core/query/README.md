# Query module walkthrough

The pure Kotlin query module owns generic state and snapshot transformations,
while feature ViewModels own coroutine lifetime and result admission.

## Origin and reading order

Extracted 2026-10-08 with Kotlin 2.3.20 and Java 17. Read
[the contract](../../../../../../../contracts/behavior/query-ui.md),
[build file](../../../../../project/core/query/build.gradle.kts), and
[QueryState](../../../../../project/core/query/src/main/kotlin/dev/mobilefoundry/query/QueryState.kt).
Only kernel is a runtime dependency. JSON is used by tests, not runtime code.
See [variance mechanics](../../../../language/kotlin-covariant-query-state-and-content-slots.md).

## Walkthrough and gotchas

The generic sealed interface accepts non-null immutable snapshots. Idle has
no payload; Loading and Failed may retain one; Loaded always contains one.
The `value` extension projects a retained or loaded snapshot. `starting`,
`settled`, and `restored` return new values without launching work.

[NotesViewModel](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesViewModel.kt)
uses the shared state through StateFlow. Its generation/activity guards still
precede settlement. The previous-result field is gone because state already
retains that snapshot. A generic wrapper does not freeze a supplied mutable
payload; callers own snapshot discipline. Null is reserved for no snapshot.

## Verification and limits

`make query-test` passed two Kotlin tests against
[nine shared cases](../../../../../../../contracts/fixtures/query/states.json)
and repeated refresh/failure/restoration after empty success. Read
[QueryStateTest](../../../../../project/core/query/src/test/kotlin/dev/mobilefoundry/query/QueryStateTest.kt).
`make android-test` passed the eight existing notes ViewModel tests and two
starter tests after extraction. Query state alone does not cancel a coroutine
or suppress an obsolete result; those checks remain in the feature tests.

## Questions and related reading

Why does Idle use Nothing? Why do generic extension functions make sense beside
a covariant interface? Follow [UI](../ui/README.md),
[the app](../../app/README.md), and
[query ownership](../../../../../../../notes/patterns/query-state-and-rendering.md).
