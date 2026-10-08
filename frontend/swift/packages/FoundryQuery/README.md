# FoundryQuery

UI-independent `QueryState<Value>` and pure snapshot transformations. Depends
only on FoundryKernel. It owns no task, observation, service, or cache.

MutationState adds pure Idle/Submitting/Succeeded/Failed write phases and
explicit reset; it retains no prior receipt or draft. See [forms/mutations](../../../../contracts/behavior/forms-mutations.md).

See [the contract](../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../notes/modules/packages/FoundryQuery/README.md).
Run `swift test` here, or `make query-test` at the repository root.
