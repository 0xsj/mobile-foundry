# Kotlin kernel walkthrough

The Kotlin/JVM kernel provides typed expected outcomes and owned failure data
without taking over platform exception or coroutine control flow.

## Source reading order

1. Read [the shared contract](../../../../../../../contracts/behavior/kernel.md)
   and [expected failures and diagnostics](../../../../../../../notes/concepts/expected-failures-and-diagnostics.md).
2. Read [Failure.kt](../../../../../project/core/kernel/src/main/kotlin/dev/mobilefoundry/kernel/Failure.kt)
   for categories, metadata, timing, field ownership, and projection.
3. Read [Outcome.kt](../../../../../project/core/kernel/src/main/kotlin/dev/mobilefoundry/kernel/Outcome.kt)
   with [the language explanation](../../../../language/kotlin-outcomes-and-owned-payloads.md).
4. Read [FailureTest.kt](../../../../../project/core/kernel/src/test/kotlin/dev/mobilefoundry/kernel/FailureTest.kt)
   and [OutcomeTest.kt](../../../../../project/core/kernel/src/test/kotlin/dev/mobilefoundry/kernel/OutcomeTest.kt)
   beside [the shared fixtures](../../../../../../../contracts/fixtures/kernel/failures.tsv).
5. Read [the build configuration](../../../../../project/core/kernel/build.gradle.kts)
   for the test dependency and canonical-fixture directory property.

## Control and data flow

An Ok stores a value; an Err stores a typed problem. Covariance and Nothing
allow each branch to satisfy the complete result type. Mapping invokes only
the active callback; the other branch retains its payload. Mapping catches
nothing, so a callback defect or cancellation exception escapes unchanged.

Failures carry metadata and case-specific data. Invalid snapshots and wraps
the supplied map. RateLimited carries optional admitted timing. Public
projection redacts internal message/code and retains request identifiers;
other failure values preserve their immutable payload.

## Verification and limits

Observed 2026-10-08 with Kotlin 2.3.20, Gradle 9.1.0, and JDK 17 toolchain.
Nine kernel JUnit tests passed, including the same 13 fixture cases as Swift,
mutation isolation, timing bounds, and original callback exception identity.
The Android debug APK assembled and the two existing starter tests passed.

From the repository root:

```sh
make kernel-test
make android-build
make android-test
```

These are host behavior checks and consumer compilation. No emulator or device
UI scenario was run. Throwing a CancellationException in a synchronous callback
does not prove a future coroutine adapter preserves job cancellation. Test
fixtures are read through a Gradle property and are not bundled in the app.

## Next reading questions

- What do out and Nothing contribute to a branch with only one payload?
- Why is a val Map insufficient to guarantee immutable failure fields?
- Why should an HTTP adapter classify known dependency failures without
  enclosing domain logic in a catch-all block?
