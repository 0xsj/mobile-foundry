# Native kernel construction specification

## Status and scope

First slice: typed outcomes, failure variants, and public projection in the
existing Swift package and Kotlin/JVM module. The consumer is a future native
service or repository. Its caller receives a value, handles the expected
failure branch explicitly, and projects details at a public boundary.

Architecture and expected-failure policy are established. This specification
selects standard Swift `Result`, a two-parameter Kotlin `Outcome`, immutable
failure payloads, and an explicit `publicInfo` operation. Transport, persistence,
UI, provider integration, and asynchronous adapters are deferred.

## Exemplar workflow

A caller receives an invalid-input failure with an `email` field message, or a
rate-limit failure with optional retry timing. Mapping success skips that
failure. Public projection preserves its safe fields. An internal failure has
its message and code redacted while retaining request identifiers. A successful
optional absence remains success.

## Source documents and authority

- [Architecture](../../ARCHITECTURE.md): ownership and structured cancellation.
- [Kernel contract](../../contracts/behavior/kernel.md): controlling behavior.
- [Bento failure implementation](../../../bento/frontend/next/lib/kernel/failure.ts)
  and [result implementation](../../../bento/frontend/next/lib/kernel/result.ts):
  reference vocabulary and boundary conventions.
- [Blueprint protocol](../../../IMPLEMENTATION-BLUEPRINT-PROTOCOL.md): construction
  and verification procedure.
- [Learning workflow](../NOTES.md): source walkthroughs and language notes.

The contract adopts Bento's ten failure kinds. JavaScript's diagnostic WeakMap
and runtime object guards do not transfer into these statically typed native
values. No universal exception conversion is introduced.

## Nouns states and invariants

`AppResult` names the foundation result. `Outcome` is Kotlin's generic two-case
sum. `FailureKind` supplies stable identifiers; `FailureMeta` supplies required
message and optional code and IDs. `Failure` is a closed variant set.
`RetryAfter` admits nonnegative signed 64-bit millisecond values. These types
have no mutable lifecycle, global state, or externally owned resources.

## Workflow and side effect ordering

Construction captures metadata and owned payloads. Mapping invokes exactly the
callback for the active branch. Projection selects public fields. Unexpected
Kotlin callback errors escape; Swift standard mapping requires non-throwing
callbacks. These steps perform no I/O, logging, retries, or task
creation. Kotlin's validation-map constructor copies and wraps the input;
Swift's value semantics isolate dictionary mutation.

## Expected file tree

All listed source, tests, and fixtures are required. Paths are relative to the
repository root.

```text
contracts/behavior/kernel.md
contracts/fixtures/kernel/failures.tsv
frontend/swift/packages/FoundryKernel/
  Sources/FoundryKernel/AppResult.swift
  Sources/FoundryKernel/Failure.swift
  Tests/FoundryKernelTests/OutcomeTests.swift
  Tests/FoundryKernelTests/FailureTests.swift
  Examples/ThrowingMap.swift
frontend/kotlin/project/core/kernel/
  src/main/kotlin/dev/mobilefoundry/kernel/Outcome.kt
  src/main/kotlin/dev/mobilefoundry/kernel/Failure.kt
  src/test/kotlin/dev/mobilefoundry/kernel/OutcomeTest.kt
  src/test/kotlin/dev/mobilefoundry/kernel/FailureTest.kt
```

Update the kernel module README, native notebook indexes, root notes index,
architecture coverage, and test commands. Each native notebook requires a
mirrored kernel module walkthrough and language explanation; root concepts
explain the shared expected-failure and diagnostic boundary. No new runtime
dependency is permitted. Kotlin test support uses the existing JUnit version.
The empty Swift source and example test are replaced.

## File specifications

### Swift AppResult and Kotlin Outcome

Role and ownership: kernel result composition. Swift's file owns only the
`AppResult<Value>` alias. Kotlin owns `Outcome<Value, Problem>`, `Ok(value)`,
`Err(error)`, the equivalent alias, and `map`, `mapError`, and `flatMap`.

Inputs are typed values and callbacks. Outputs obey the active-branch contract.
No callback error is caught and there are no external side effects. Swift uses
standard-library mapping operations rather than maintaining duplicate helpers.
Verification: branch call counts, successful absence, chained refusal, generic
error transformation, and Kotlin propagation of a callback defect. Swift's
required compiler example uses only standard `Result` and proves its mapping
closure rejects throwing work; it is outside production and test targets.

### Native Failure files

Role and ownership: closed vocabulary, immutable metadata, required variant
payloads, retry value admission, and public projection. Dependencies are only
language and platform standard libraries; Kotlin may use JDK collection wrappers
in this JVM module. No framework, provider, exception cause, or HTTP decoder.

`FailureKind` exposes the contract's stable strings. `FailureMeta` construction
stores the message, code, request ID, and correlation ID without sanitization.
`RetryAfter` factories accept a signed 64-bit millisecond value and return absence
for negative input. `Failure` variants expose `kind` and `meta`. Invalid requires
a map and snapshots it; rate-limited accepts optional `RetryAfter`.
`publicInfo` returns the same kind, applies internal redaction, and preserves
the allowed payload. It has no side effects and is idempotent.

Verification: shared fixture expectations, complete kind coverage, owned field
maps, retry bounds, and repeated projection. No error classification is inferred
from platform exception text or HTTP status.

### Native tests and shared fixtures

Role and ownership: independent behavioral oracles for the kernel contract.
`OutcomeTests` and `OutcomeTest` test composition. `FailureTests` and
`FailureTest` parse one canonical TSV fixture file and construct native variants
through exhaustive switches. A test-only fixture loader resolves the repository
file; no fixtures enter the app or library artifact.

The fixture columns name kind, metadata, variant payload, and expected public
message/code. A final case ID names each scenario uniquely. Blank cells mean
absence, except a missing required message would
be a malformed fixture. Load errors fail tests. All ten kinds must appear.
Test dependencies are Swift Testing/Foundation and existing JUnit/JDK support.

## Cross file dependency map

The result alias depends on `Failure`; failure values do not depend on outcomes.
Tests depend on the kernel and canonical fixtures. Catalog apps retain their
existing library dependency. Notes link to source and contract without defining
an additional runtime contract.

## Transport and persistence mapping

Not applicable. Public projection is a value transformation, not serialization.
HTTP and provider adapters later admit unknown external input, classify dependency
failures, preserve cancellation, and report original defects at their own boundary.

## Verification plan

Run Swift kernel tests and Kotlin kernel tests, then build the existing iOS and
Android consumers. Run the Android starter tests in the same Gradle invocation
as the new kernel tests. Run `make notes-check` and `git diff --check` after
documentation updates. Build success does not establish device or screen behavior.

## Construction order

1. Establish the contract, specification, and shared fixture expectations.
2. Implement native failure values and projection.
3. Implement result aliases and Kotlin composition.
4. Add native behavioral tests consuming the shared fixtures.
5. Add language, module, and shared concept notes; update reading orders.
6. Run verification and record actual results and material deviations.

## Open decisions and unknowns

Future operation-specific failure narrowing, diagnostic reporting, cancellation
adapters, and public encoding need their own slice contracts. No unresolved
decision blocks this value-only kernel implementation.

## Deviation record

| Location | Initial specification | Actual choice | Reason |
| --- | --- | --- | --- |
| Swift result mapping | Assumed throwing callbacks could propagate | Standard mapping requires non-throwing callbacks; compiler example included | Swift 6.2.3 rejected the throwing callback. Keep standard Result rather than duplicate it; controlling contract corrected. |

## Completion criteria

The implementations satisfy the shared fixtures and behavioral tests, both
catalog consumers compile, and the learning handoff links the source, language
mechanics, evidence, limits, and next questions.

## Observed completion

On 2026-10-08, seven Swift kernel tests, nine Kotlin kernel tests, and the two
Android starter tests passed. The unsigned iOS simulator consumer build and
Android debug assembly succeeded. The Swift throwing-map example was rejected
with the expected diagnostic. Documentation links and native example labels
passed `make notes-check`. No app was launched or asynchronous adapter exercised.
