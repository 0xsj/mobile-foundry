# Gradle and Android bootstrap

One Gradle build owns the Android app and Kotlin/JVM kernel, with wrapper and
version catalog settings kept alongside the native project.

## Origin

Observed during initialization on 2026-10-07. The generated app plus kernel
module built a debug APK and passed its two starter unit tests.

| Setting | Initial value |
| --- | --- |
| Gradle wrapper | 9.1.0, with distribution checksum |
| Android Gradle Plugin | 9.0.1 |
| Kotlin | 2.3.20 |
| Compose BOM | 2026.03.01 |
| kotlinx.coroutines version catalog entry | 1.10.2 |
| Android Lifecycle version catalog entry | 2.10.0 |
| Java compilation and toolchain | 17 |
| Minimum SDK | 24 |
| Compile and target SDK | 36 |

The build was invoked using Android Studio's bundled JDK and the local Android
SDK. These paths are machine configuration, not portable source dependencies.

## What and why

Settings register the app and kernel modules. The root build declares plugin
versions through aliases, the version catalog names dependency versions, and
the wrapper supplies the selected Gradle distribution. The app depends on the
kernel rather than duplicating reusable source inside its screen package.

## Example

From the repository root:

```sh
make android-build
make android-test
```

The first command builds the kernel and debug APK; the second runs host unit
tests. Instrumented tests need a device or emulator and a separate command.
Initialization observed successful assembly and two passing host tests.

## Flow sharing in the generated screen

`stateIn(scope, started, initialValue)` shares upstream values through a
StateFlow under the supplied scope. `WhileSubscribed(5000)` supplies a
five-second stop timeout after the last subscriber leaves. This policy is
independent of Kotlin's sealed-state syntax.

The version catalog declares coroutines 1.10.2; the relevant upstream contract
and implementation are in the tagged [Share.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/operators/Share.kt)
and [SharingStarted.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/SharingStarted.kt).
This explains configuration inspected in the starter. Its host tests currently
observe only the initial Loading value, so they do not establish the later
collection or stop-timeout behavior.

## Gotchas

A version catalog entry alone is not a report of every resolved transitive
dependency version. Recheck dependency resolution when investigating a
framework-specific issue or changing libraries. The initialized Kotlin/JVM
module was empty at bootstrap; that initial build did not verify kernel
behavior. The 2026-10-08 slice adds JUnit tests and a Gradle system property
pointing to canonical repository fixtures; see [the kernel walkthrough](../modules/project/core/kernel/README.md)
for current verification. These fixtures are test inputs rather than app resources.

## Used in and related

Use one wrapper and version catalog for the catalog's growing native modules.
Revisit these observations when plugins, toolchains, SDK baselines, dependency
resolution, or sharing configuration change. The module walkthrough links the
actual settings, view model, screen, and tests.
