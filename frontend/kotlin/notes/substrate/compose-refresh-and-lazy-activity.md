# Compose refresh and lazy activity

Claim: PullToRefreshBox owns gesture presentation while a destination effect
owns delayed work and a hoisted model owns stable feed values.

## Origin and evidence

Added 2026-10-09 for the seventh UI batch. Kotlin 2.3.20, AGP 9.0.1,
Compose BOM 2026.03.01, Material3 1.4.0, JDK 17, minSdk 24 / compileSdk 36.
Primary references: [pull to refresh](https://developer.android.com/develop/ui/compose/components/pull-to-refresh),
[lazy lists and keys](https://developer.android.com/develop/ui/compose/lists)
and [effect lifecycle](https://developer.android.com/develop/ui/compose/side-effects).
The module walkthroughs link actual source and checks; no remote feed is involved.

## What and why

RefreshContainer wraps PullToRefreshBox and hides its experimental opt-in at the
adapter. The caller supplies isRefreshing, onRefresh and a bounded scrollable
child. It does not create a coroutine scope. The app supplies LazyColumn and
coordinates refresh/page admission in one ephemeral state holder.

```kotlin
// Excerpt: the destination owns effect lifetime; values remain above route replacement.
LaunchedEffect(values.request) { values.completeRequest() }
DisposableEffect(values) { onDispose { values.cancelPending() } }
```

The request generation changes only after admission; repeated requests during
work cannot start another page. delay is cancellable. finally clears busy/loading
on completion or cancellation. The disposal cleanup also handles a request whose
effect has not started yet. Reopening the destination retains records and expanded
IDs, while pending work does not continue offscreen. remember is ephemeral;
process restoration is not promised for this fixture.

Native lazy content needs stable keys; expansion is a set of record IDs held
outside the row composition. Text uses native maxLines/ellipsis and a separate
TextButton. Timeline decorations carry no accessibility identity. AvatarGroup
replaces passive art descriptions with a supplied summary for all members.

## Gotchas, actual use and limits

The first activity test helper tried to find an offscreen footer directly; it
failed because the lazy list had not realized that item. performScrollToNode on
the list locates/composes the item first. This was a test assumption, not evidence
that pagination failed.

Emulator checks execute an actual swipe-down refresh, duplicate admission,
failed-page retry with retained rows, exhaustion, expansion through refresh,
destination disposal and return, and family/theme/navigation retention. Existing
component regressions run separately in the same focused suite. These establish
native UI behavior on API 36, not server paging, process-restored feeds,
comprehensive TalkBack, localization or physical-device coverage.
Next: which feed state belongs in a route-owned ViewModel with a real domain port?

## Related

- [UI walkthrough](../modules/project/core/ui/README.md#activity-and-paged-collections).
- [Activity gallery](../modules/project/app/README.md#activity-gallery).
- [Effect/request lifetime](okhttp-and-compose-effect-lifetime.md).
- [Shared refresh/page ownership](../../../../notes/patterns/refresh-and-pagination-ownership.md).
