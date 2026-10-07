# Android starter walkthrough

The generated app separates data production, view-model state, and Compose
rendering, while the added kernel module establishes a reusable build boundary.

## Origin

The Android CLI's generated starter and initialized Gradle modules, inspected
2026-10-07. This note mirrors `project/app/` and records the starter's current
behavior rather than adopting all of it as foundation policy.
[Architecture](../../../../../../ARCHITECTURE.md) owns the proposed boundaries.

## Reading order

1. [App build](../../../../project/app/build.gradle.kts) and
   [settings](../../../../project/settings.gradle.kts): app and kernel registration.
2. [MainActivity.kt](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/MainActivity.kt):
   Compose content, theme, and navigation root.
3. [Navigation.kt](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/Navigation.kt):
   back stack and the Main screen entry.
4. [DataRepository.kt](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/data/DataRepository.kt):
   repository interface and the generated in-memory data flow.
5. [MainScreenViewModel.kt](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/main/MainScreenViewModel.kt):
   state transformation and the three UI variants.
6. [MainScreen.kt](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/main/MainScreen.kt):
   collection, state branches, and the data-only rendering overload.
7. [Starter unit tests](../../../../project/app/src/test/java/dev/mobilefoundry/catalog/ui/main/MainScreenViewModelTest.kt)
   and [instrumented screen test](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/main/MainScreenTest.kt).

## Walkthrough

`MainActivity` sets Compose content inside the generated theme and surface,
then calls `MainNavigation`. Navigation begins with the Main key and supplies
`MainScreen` with its navigation callback and layout modifier.

The screen's default view-model factory creates `MainScreenViewModel` with
`DefaultDataRepository`. The repository exposes `Flow<List<String>>`; its
implementation emits a list containing `Android`. It has no storage or network
adapter.

The view model maps emitted lists to Success, converts caught upstream
exceptions to Error, and calls `stateIn` with `viewModelScope`, Loading, and
`SharingStarted.WhileSubscribed(5000)`. Under the documented sharing policy,
the upstream starts when subscribed and waits five seconds after the last
subscriber disappears before stopping. This is a framework configuration,
not evidence of a five-second delay before displaying data. See the versioned
[sharing note](../../../substrate/gradle-and-android-bootstrap.md#flow-sharing-in-the-generated-screen).

The screen collects the state using `collectAsStateWithLifecycle`. Android's
[Compose state guide](https://developer.android.com/develop/ui/compose/state#other-supported-types-of-state)
describes this lifecycle-aware conversion from Flow to Compose State. The
Kotlin [state type note](../../../language/kotlin-sealed-ui-states-and-data-classes.md)
explains the variants and `by` syntax.

Loading renders no content, Success passes its list into the data-only screen
overload, and Error displays the Throwable's message. The data-only overload
renders one greeting per list item. `onItemClick` is accepted but unused, so
this screen has no item-driven navigation yet.

## Gotchas

The Error variant carries an arbitrary Throwable and the UI prints its message.
The foundry's intended policy uses typed expected failures at owned boundaries;
the starter needs a deliberate boundary before serving real dependencies.
The current blank Loading state also needs a real catalog example later.

The app depends on `:core:kernel`, but that module has no runtime types. A
successful dependency build establishes wiring, not an implemented kernel.

## Verification and limits

Initialization on 2026-10-07 ran the operations exposed by these root targets:

```sh
make android-build
make android-test
```

The kernel build, debug APK assembly, and two starter unit tests passed. Both
unit tests assert the initial Loading state. Despite the second test's name,
it does not exercise saving or observe Success. The instrumented test checks
greetings from supplied data, but was not run; the app was not launched on an
emulator or device. Repository collection and screen transitions still need
meaningful runtime checks when this scaffold becomes the catalog.

## Questions for the next session

- Why does observing Loading once fail to establish the later Success state?
- Which UI behavior belongs in a data-only component, and which needs a
  view model or feature boundary?
- Where should a typed dependency failure be introduced before a real API
  replaces the generated repository?

## Related

[Toolchain and sharing configuration](../../../substrate/gradle-and-android-bootstrap.md),
[Kotlin reading order](../../../README.md), and
[native setup](../../../../../../docs/SETUP.md).
