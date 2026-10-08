# Query core

Pure Kotlin `QueryState<Value>` and snapshot transformations. Depends only on
kernel; payloads are non-null immutable snapshots supplied by callers.
No coroutine, StateFlow, Android, service, or cache is owned here.

MutationState adds pure Idle/Submitting/Succeeded/Failed write phases and
explicit reset; it retains no prior receipt or draft. See [forms/mutations](../../../../../contracts/behavior/forms-mutations.md).

See [the contract](../../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../../notes/modules/project/core/query/README.md).
Run `./gradlew :core:query:test` from the Gradle root, or `make query-test`
from the repository root.
