# Android catalog walkthrough

The app now navigates to an injected health example while retaining its generated starter data flow.

## Origin

Android starter initialized 2026-10-07; HTTP example added 2026-10-08 with
Kotlin 2.3.20, Compose BOM 2026.03.01 and API 36. This note mirrors `project/app/`.
[HTTP and health contract](../../../../../../contracts/behavior/http.md) controls the new service behavior; generated starter policy is separate.

## Reading order

1. [App build](../../../../project/app/build.gradle.kts) registers kernel/services and the AndroidX instrumented test runner.
2. [MainActivity](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/MainActivity.kt) supplies theme, surface and MainNavigation.
3. [Navigation](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/Navigation.kt) and [Navigation keys](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/NavigationKeys.kt) define Main and HealthCatalog entries.
4. [Main screen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/main/MainScreen.kt) renders an always-available HTTP health button and starter data.
5. [HealthCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/health/HealthCatalogScreen.kt) owns selection, effect lifetime, phase and service composition.
6. [Services walkthrough](../core/services/README.md) and [HTTP walkthrough](../core/http/README.md) explain the reusable operations.
7. [Health UI tests](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/health/HealthCatalogScreenTest.kt) exercises health scenarios and request replacement on a device.

## Walkthrough

The serializable HealthCatalog NavKey enters the Navigation3 back stack.
The entry supplies a back callback and safe-drawing padding. The health screen
remembers scenario, replay counter and phase; the selected keys own its
LaunchedEffect coroutine. [Suspending ports](../../../language/kotlin-suspending-ports-and-cancellation.md) explains suspend/cancellation syntax and
[Compose effect lifetime](../../../substrate/okhttp-and-compose-effect-lifetime.md) explains composition lifetime.

Loading is followed by Success(Health) or Failed(Failure). The screen's injected
transport creates six wire scenarios; the real client and health service still
admit them. State publication checks coroutine activity. Cancellation is
rethrown. Unexpected exceptions go to Log.e and a deliberate generic failure.
Expected failure rendering uses publicInfo, category copy, admitted validation
fields and optional retry timing. Standard Material controls are used; screen
layout scrolls vertically and scenario chips scroll horizontally.

## Retained starter

The Main screen's generated repository still emits `Android` in memory. Its
view model maps Flow data to Success, catches upstream Throwable into Error,
and uses WhileSubscribed(5000) with Loading. [Starter sharing configuration](../../../substrate/gradle-and-android-bootstrap.md) explains that subscription
policy. Success renders greetings; its error message remains generated starter
behavior. The kernel and health workflow do not adopt that arbitrary Throwable
presentation. Main's navigation callback is now used by the health button.

## Verification and limits

`make android-build` and `make android-test` passed: debug APK, core modules,
and host tests. The two existing starter tests still assert only Loading;
they do not establish a later repository Success transition. The health
instrumented tests require a local emulator/device and use the configured
AndroidJUnitRunner. `make android-ui-test` passed three instrumented tests on API36_Test (Android
16): the starter greeting test and two health checks covering healthy and all
failure scenarios plus replacement of an in-flight request. These device
checks also rebuilt the final app/test consumers.

The injected health example does not establish persistence, native provider
sessions, production network policy, background work, accessibility or graphics
performance. Kernel and adapter host tests provide independent evidence below
Compose; [Verification techniques](../../../../../../notes/techniques/shared-fixtures-and-native-adapters.md) explains the distinction.

## Questions for the next session

- Why is composition lifetime different from viewModelScope?
- Which layout/state behavior is ready to extract into a reusable UI component?
- Why should generated raw Throwable rendering remain outside real dependencies?

## Related

[State types](../../../language/kotlin-sealed-ui-states-and-data-classes.md), [Kotlin reading order](../../../README.md), and [Setup](../../../../../../docs/SETUP.md).
