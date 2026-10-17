# Java API desugaring on the Android catalog

Language bytecode compatibility does not by itself make newer Java library APIs
available on an older Android runtime.

## Origin and evidence

Observed 2026-10-17 in the installation registry slice: AGP 9.0.1, compilation
JDK 17, minSdk 24, desugar_jdk_libs 2.1.5. The catalog APK builds and its native
vault tests pass on API36_Test/Android 16. This is configuration/build evidence,
not execution on API24/25.

## What and why

Services use java.time.Instant for admitted wire timestamps and java.util.Base64
for canonical installation proof. The catalog enables coreLibraryDesugaring in
compileOptions and adds the matching dependency. AGP rewrites supported API calls
and packages library implementations; sourceCompatibility/jvmToolchain select
language/compilation behavior separately.

Android's [desugaring guide](https://developer.android.com/studio/write/java8-support)
explains setup. The upstream [changelog](https://github.com/google/desugar_jdk_libs/blob/master/CHANGELOG.md)
records Base64 support from 2.0.4 and the selected 2.1.5 release. Keep version and
configuration aligned; do not infer all desktop JDK APIs are supported.

## Example

Excerpt from the catalog build:

```kotlin
// Excerpt
compileOptions { isCoreLibraryDesugaringEnabled = true }
// Inside dependencies:
coreLibraryDesugaring(libs.desugar.jdk.libs)
```

## Gotchas and actual use

A JVM unit test runs with the desktop JDK and cannot expose a missing Android
runtime class. A current API36 emulator also has native implementations, so keep
the older-OS acceptance limit explicit. Generated local-only profiles select a
different, smaller source closure; the new remote registry is not silently copied
into them. A future connected profile must carry this dependency/configuration.

Read [the app walkthrough](../modules/project/app/README.md#native-installation-ownership--2026-10-17)
and [Services](../modules/project/core/services/README.md#installation-registry--2026-10-17).
Related: [Gradle launcher and toolchain](gradle-and-android-bootstrap.md#gradle-launcher-and-compilation-toolchain).
Next: run the connected registry on API24/25 before claiming that runtime range.
