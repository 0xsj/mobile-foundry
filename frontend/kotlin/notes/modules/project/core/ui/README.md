# Async UI module walkthrough

The Android UI library renders generic query state with Material controls,
caller copy and a composable domain content slot.

## Origin and reading order

Extracted 2026-10-08 with AGP 9.0.1, Kotlin 2.3.20, and Compose BOM 2026.03.01.
Read [the contract](../../../../../../../contracts/behavior/query-ui.md),
[build file](../../../../../project/core/ui/build.gradle.kts), then
[QueryContent](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/QueryContent.kt).
Compare [NotesScreen](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesScreen.kt)
and [QueryCatalogScreen](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/query/QueryCatalogScreen.kt).

## Walkthrough and gotchas

The library uses the Android library and Compose compiler plugins, with AGP's
built-in Kotlin support. It depends on query/kernel and native UI libraries,
not services or app code. The host supplies MaterialTheme and scrolling.

QueryContent switches on QueryState, labels indeterminate progress, renders
retained content or empty copy, projects failures through publicInfo, and
forwards button callbacks. QueryCopy can receive localized application strings.
The content slot is composable; it receives a typed payload, not a service.
See [slot mechanics](../../../../language/kotlin-covariant-query-state-and-content-slots.md).

## Verification and limits

`make android-build` assembled the library and catalog. `make android-ui-test`
passed all ten app instrumented tests on API36_Test/Android 16. Four new
[QueryContentTest checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/query/QueryContentTest.kt)
cover the nine-state presentation matrix, progress semantics, exact callback
counts, internal failure copy, and gallery navigation with empty refresh/cancel.
The six existing catalog tests still pass, including both notes providers.

The installed Compose test API requires a range argument for
hasProgressBarRangeInfo; the tests explicitly match Indeterminate. Semantic
checks do not establish TalkBack usability or large-text/dark-mode layout.
Tokens, forms, and further controls remain separate capabilities.

## Questions and related reading

Why is emptiness supplied by the feature? Why can Refresh stay available while
loading without the component owning a coroutine? Read
[query ownership](../../../../../../../notes/patterns/query-state-and-rendering.md)
and [Compose lifetime](../../../../substrate/okhttp-and-compose-effect-lifetime.md).
Primary references: [AGP 9 built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes#built-in-kotlin)
and [native progress indicators](https://developer.android.com/develop/ui/compose/components/progress).
