# Query state and async presentation

Status: Established for the reusable state/UI slice, 2026-10-08.

## State values

QueryState<Value> is a UI-independent snapshot: Idle, Loading(previous?),
Loaded(value), or Failed(failure, previous?). Absence of a snapshot differs
from a successful empty value. Payloads must be immutable snapshots; the
generic container does not copy or freeze arbitrary reference values.
Kotlin payloads are non-null; model successful absence with a domain value.

`value` projects the loaded or retained snapshot, `failure` projects only a
Failed state's failure, and `isLoading` identifies Loading. `starting()` returns
Loading retaining the current snapshot. `settled(result)` returns Loaded on
success, or Failed retaining the current snapshot on expected failure.
`restored()` returns Loaded if a snapshot exists, otherwise Idle. These are pure
transformations; they never start, cancel, admit, or retry asynchronous work.

The feature state owner remains responsible for task/coroutine lifetime,
latest-result admission, service selection, and unexpected-defect reporting.
The existing notes lifetime contract continues to apply. No shared request
runtime, query keys, cache, deduplication, or invalidation is introduced.

## Native presentation

QueryContent accepts QueryState, caller-selected copy, an emptiness predicate,
a content slot, and refresh/cancel callbacks. It owns no service, task,
ViewModel, navigation, scrolling, or query lifetime. Its host owns layout and
theme. SwiftUI emits rows suitable for a List/Section or stack; Compose emits
a Column suitable for a screen or scrolling host.

| State | Presentation |
| --- | --- |
| Idle | Idle copy and Refresh |
| Loading without snapshot | Labeled indeterminate progress, Cancel loading, Refresh |
| Loading with snapshot | Refresh progress, retained content or empty copy, Cancel loading, Refresh |
| Loaded | Content or empty copy, Refresh |
| Failed without snapshot | Public failure copy, Retry, Refresh |
| Failed with snapshot | Public failure copy, retained content or empty copy, Retry, Refresh |

Refresh remains available while loading; replacement policy belongs to the
caller. Cancel is exposed only while loading, Retry only on failure. Rendering
and state changes never invoke callbacks; user actions invoke the corresponding
callback once. Failure copy uses kernel publicInfo so internal details are not
shown. Caller copy can be localized before injection. Controls use native text
and progress semantics without fixed text sizes or a private color palette.

## Examples and evidence

Notes uses QueryState<List<Note>> plus the component. A separate Async UI
patterns destination uses a scalar string snapshot and manually selectable
states, including refreshing/failing after an empty success. It proves the
component does not depend on notes or services.

Both query suites consume shared state fixtures. Existing notes race tests
continue to exercise feature-owned orchestration. Native rendering checks
cover the presentation matrix, public failure copy, and callback routing.
Builds and semantic UI checks do not establish VoiceOver/TalkBack quality,
large-text layout, visual design, or physical-device performance.
