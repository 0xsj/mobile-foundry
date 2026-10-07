# Kotlin kernel module

This Kotlin/JVM library is the home for outcomes, typed failures, identifiers,
clocks, and diagnostic metadata. It has no Android or UI dependencies and is
linked by the catalog application.

The first slice implements `Outcome<Value, Problem>`, `AppResult<Value>`, the
failure vocabulary, immutable variant payloads, retry timing, and public
projection. [The shared contract](../../../../../contracts/behavior/kernel.md)
controls behavior; [the module walkthrough](../../../notes/modules/project/core/kernel/README.md)
links the source, tests, and language mechanics. Identifiers and clocks remain
future capabilities.

From the Gradle project directory, with its JDK configured:

```sh
./gradlew :core:kernel:test
```

Tests read the canonical repository fixtures through a Gradle test property.
The library has no additional runtime dependency; JUnit uses the existing
version catalog for test support.
