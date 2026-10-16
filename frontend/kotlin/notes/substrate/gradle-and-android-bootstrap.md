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

## Editor dependency imports

Observed on 2026-10-08 with VSCodium's `fwcd.kotlin` extension 0.2.36,
Kotlin Language Server 1.3.13, and Gradle 9.1.0: the editor reported unresolved
`kotlinx.serialization` imports in `HealthService.kt` while Gradle compiled it.
The service compile classpath already contained serialization JSON 1.9.0 via
the HTTP module's `api` dependency. An editor diagnostic alone does not establish
that a build dependency is missing.

The server's logs showed failed configuration-cache serialization in its
injected Gradle tasks and an incomplete source classpath. Its
[Gradle resolver](https://github.com/fwcd/kotlin-language-server/blob/1.3.13/shared/src/main/kotlin/org/javacs/kt/classpath/GradleClassPathResolver.kt)
runs temporary init scripts that add `kotlinLSP*` tasks. Inspection of the
installed scripts showed task actions reading live `Project` state. Replaying
the service dependency task with `--no-configuration-cache` produced the JSON,
coroutines, and other dependency paths.

The [root build](../../project/build.gradle.kts) now marks only those injected
tasks with `notCompatibleWithConfigurationCache`. Gradle's
[task opt-out documentation](https://docs.gradle.org/current/userguide/configuration_cache_debugging.html#config_cache:task_opt_out)
explains why incompatible task runs can succeed while discarding their cache
entry. The observed import run completed with the expected dependency paths
and discarded its entry with reported compatibility problems. Normal compiler
and HTTP/service test checks succeeded with up-to-date outputs and stored their
configuration cache entry.

After **Developer: Reload Window**, the server logged no diagnostics for
`HealthService.kt`, and VSCodium showed zero errors and zero warnings. Diagnostics
remain enabled. This verifies the reported service imports, not every Android
or Compose editor capability. Revisit this workaround when changing the editor
extension, language server, or Gradle version; it can be removed when the
injected tasks become compatible or are no longer used.

## Used in and related

Use one wrapper and version catalog for the catalog's growing native modules.
Revisit these observations when plugins, toolchains, SDK baselines, dependency
resolution, or sharing configuration change. The module walkthrough links the
actual settings, view model, screen, and tests.

## Clean product identity

Claim: Android installed identity, source namespace and resource branding can be
separate product inputs while reusable modules keep their original names.

Added 2026-10-10 with AGP 9.0.1 / Gradle 9.1.0 / Kotlin 2.3.20. The separate
product module reads applicationId/version from product.properties using Gradle's
file-content provider and java.util.Properties. Namespace stays
dev.mobilefoundry.product, which generates R independently of applicationId.
MainActivity has a fully qualified manifest class name. Runtime app_name supplies
both the launcher label and the Today screen's display name.
Google documents [application ID and namespace](https://developer.android.com/build/configure-app-module);
Gradle documents [file-content providers](https://docs.gradle.org/current/javadoc/org/gradle/api/file/FileContents.html).
An alternative is direct literals in build.gradle.kts; the small properties file
makes later generator identity edits explicit. Resource branding remains Android
XML so normal escaping/localization rules apply.

Example: edit product.properties and src/main/res/values/strings.xml, then run
`../gradlew -p .. :product:assembleDebug` from the product folder. Namespace/source
refactoring is optional and separate. ProductRoot keeps selected ID and material
in rememberSaveable owners above themed branches; native Activity recreation
restores them. This is saved instance state, not durable user preferences.

Observed: default product debug/test APKs build with configuration cache stored.
Two actual MainActivity checks pass for four tabs, material changes, recreation,
runtime label and absence of camera/internet permissions. Independently renamed
output is unverified. The future settings overlay selects only product/ui/query/
kernel; wrapper, version catalog and relative fixture locations must travel with
those modules. Do not copy local.properties or SDK/cache paths as source inputs.

Read [product module](../modules/project/product/README.md) and
[extraction closure](../../../../notes/patterns/product-entry-and-extraction-closure.md).

## Gradle launcher and compilation toolchain

Claim: the JVM running Gradle and the JDK compiling application/library source
are separate selections; a toolchain declaration does not override stale JAVA_HOME.

Observed 2026-10-10 with Gradle 9.1.0 / AGP 9.0.1 / Android Studio's JDK 21.0.6.
The copied projects declare jvmToolchain(17) and Java source/target 17. The launcher
uses JAVA_HOME or Android Studio's selected JVM; Gradle resolves the compilation
toolchain separately. The settings retain the Foojay resolver for missing JDKs.
Gradle documents [toolchain selection](https://docs.gradle.org/current/userguide/toolchains.html),
[Java 25 support in 9.1](https://docs.gradle.org/9.1.0/release-notes.html) and
[its launcher version progression](https://blog.gradle.org/gradle-is-javamaxxing).

The CLI doctor originally saw a nonexistent JAVA_HOME pointing at an old JDK 11.
With Studio's actual JDK 21 selected, native build/unit/Activity checks pass.
Doctor accepts launcher versions 17–25 for the pinned Gradle and reports the
configured compilation toolchain separately. It does not download or prove that
toolchain available; a real build provides that evidence. The earlier table's
17 describes compilation, not a claim that Studio's launcher is version 17.

Example: pass ANDROID_JAVA_HOME and ANDROID_SDK to generated Make commands, or
set JAVA_HOME/ANDROID_HOME deliberately in the terminal. Keep local.properties,
SDK locations and caches out of copied source. Product-only settings plus the
checked wrapper/version catalog and retained fixture depth built the generated
Orbit Lab independently. Its feature screen imports dev.mobilefoundry.product.R
explicitly even when the installed app ID is com.example.orbitlab.
Read [the product output record](../modules/project/product/README.md#independent-cli-output)
and [CLI usage](../../../../cli/README.md).
