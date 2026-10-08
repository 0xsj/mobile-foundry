# Query package walkthrough

FoundryQuery owns generic state values and pure transitions, while feature
stores retain responsibility for asynchronous result admission.

## Origin and reading order

Extracted 2026-10-08 from the notes store. Read the
[contract](../../../../../../contracts/behavior/query-ui.md),
[manifest](../../../../packages/FoundryQuery/Package.swift), then
[QueryState](../../../../packages/FoundryQuery/Sources/FoundryQuery/QueryState.swift).
The package depends only on kernel and uses no observation or UI framework.
See [generic mechanics](../../../language/swift-generic-query-state-and-content-builders.md).

## Walkthrough

A loaded empty list has a present snapshot. `starting()` retains it in Loading;
`settled(with:)` either replaces it on success or retains it beside a failure.
`restored()` drops progress/failure and returns Loaded, including an empty value.
Before the first success, restoration instead returns Idle.

[NotesStore](../../../../apps/FoundryCatalog/Sources/Notes/NotesStore.swift) now
uses these values instead of a feature enum and a second previous-result field.
Its generation checks still precede settlement. The
[UI package](../FoundryUI/README.md) consumes the same state with a scalar
payload in the gallery. Neither consumer requires shared cache identity.

## Verification and limits

`make query-test` passed two Swift tests, consuming
[nine shared cases](../../../../../../contracts/fixtures/query/states.json) and
checking repeated refresh/failure after an empty success. Read
[the tests](../../../../packages/FoundryQuery/Tests/FoundryQueryTests/QueryStateTests.swift).
`make ios-test` passed all seven existing notes store regressions after extraction
on iPhone 17 Pro/iOS 26.2. Pure state tests alone prove neither cancellation nor
latest-result admission; those remain feature checks.

## Questions and related reading

Why must settlement happen after the generation guard? Why is an empty previous
list different from no previous list? Follow
[query ownership](../../../../../../notes/patterns/query-state-and-rendering.md)
and the [catalog walkthrough](../../apps/FoundryCatalog/README.md).
