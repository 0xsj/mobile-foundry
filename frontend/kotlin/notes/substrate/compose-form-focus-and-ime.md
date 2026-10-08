# Compose form focus and IME

Native text controls accept focus and keyboard policy from their caller; a
ViewModel guards command admission independently of composition timing.

## Origin and evidence

The 2026-10-08 forms slice uses Kotlin 2.3.20, Compose UI 1.10.6 and Android API
24+, with device checks on API36_Test/Android 16. Follow the [UI](../modules/project/core/ui/README.md)
and [app](../modules/project/app/README.md) walkthroughs for sources and actual
verification. Primary reference: [Compose text input](https://developer.android.com/develop/ui/compose/text/user-input).
API compatibility and semantics checks do not establish every keyboard,
TalkBack announcement, configuration change or physical-device behavior.

## What and why

OutlinedTextField keeps Material editing, labeling and error presentation.
The wrapper takes KeyboardOptions and KeyboardActions without deciding what
Done means. An error supplies both `isError` and semantics error text; supporting
text names the error rather than relying on color. Mutation feedback uses a
polite live region; actual spoken timing still requires TalkBack testing.

```kotlin
// Excerpt — inside CreateNoteScreen
val focus = remember { FocusRequester() }
var wasFocused by remember { mutableStateOf(false) }
val fieldModifier = Modifier.focusRequester(focus).onFocusChanged {
    if (wasFocused && !it.isFocused) blurTitle()
    wasFocused = it.isFocused
}
```

Attach the requester and observer before the native field's internal focus
target. The remembered flag avoids treating the initial false focus event as
blur. Request focus after rejected admission; use LocalFocusManager.clearFocus
after accepted submission. IME Done and the button invoke the same action.
The catalog supplies scrolling and imePadding so the keyboard can reduce the
available area; reusable controls do not own the screen viewport.

The ViewModel sets Submitting before viewModelScope.launch. This closes the
duplicate window even if a second action runs before recomposition disables the
button. StateFlow publishes a new immutable form value; callbacks never mutate
the value captured by a composable. The navigation entry owns the ViewModel;
DisposableEffect stops current observation when the form leaves composition.

## Language and concurrency mechanics

CreateNote uses a private constructor rather than a data class: callers should
not get a public copy operation that bypasses admission. Its companion factory
returns Outcome, and a suspend fun interface permits a small injected provider
lambda. String.length counts UTF-16 units; codePointCount handles supplementary
emoji consistently with Swift scalars. This is still not grapheme counting.

The mutable memory provider uses Mutex.withLock for append and list. Acquisition
can suspend without blocking a thread, and the lock is released when the block
exits. ensureActive checks cancellation before append; the mutation itself has
no suspension point. An unmodifiable copy supplies snapshot ownership: Kotlin
List alone restricts the interface but does not prevent mutation through a cast.
The lock serializes this one instance; it supplies no remote transaction.

## Gotchas and alternatives

A canceled coroutine can return late through an uncooperative dependency.
ensureActive and the feature generation both matter: cancellation rejects a
canceled attempt, while generation rejects work from an earlier screen lifetime.
The finally block clears busy state for current cancellation; stop advances
generation before canceling. CancellationException is rethrown rather than
reported as a defect. NonCancellable is used only in controlled test providers
to prove obsolete publication is rejected, never to keep a production write alive.

The draft is ViewModel state, not process-persistent data. More fields would
need focus ordering and domain-specific cross-field rules before a generic
form abstraction is justified.

Related: [StateFlow and ports](../language/kotlin-service-interfaces-and-stateflow.md),
[cancellation](../language/kotlin-suspending-ports-and-cancellation.md), and
[shared mutation ownership](../../../../notes/patterns/forms-and-mutation-ownership.md).
