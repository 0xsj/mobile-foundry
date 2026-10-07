# Swift kernel walkthrough

The kernel returns expected failures as typed values and projects internal
details explicitly without owning networking, UI, or diagnostic reporting.

## Source reading order

1. Read [the shared contract](../../../../../../contracts/behavior/kernel.md)
   and [expected failures and diagnostics](../../../../../../notes/concepts/expected-failures-and-diagnostics.md).
2. Read [Failure.swift](../../../../packages/FoundryKernel/Sources/FoundryKernel/Failure.swift)
   for kinds, metadata, retry admission, cases, and public projection.
3. Read [AppResult.swift](../../../../packages/FoundryKernel/Sources/FoundryKernel/AppResult.swift)
   for the standard Result alias, then
   [the language explanation](../../../language/swift-result-and-failure-values.md).
4. Read [OutcomeTests.swift](../../../../packages/FoundryKernel/Tests/FoundryKernelTests/OutcomeTests.swift)
   and [FailureTests.swift](../../../../packages/FoundryKernel/Tests/FoundryKernelTests/FailureTests.swift)
   beside [the shared fixtures](../../../../../../contracts/fixtures/kernel/failures.tsv).
5. Try [the intentional compiler rejection](../../../../packages/FoundryKernel/Examples/ThrowingMap.swift)
   using the command in the language note.

## Control and data flow

The caller constructs a Failure case with its required payload. AppResult
selects success or failure; standard Result mapping touches only its matching
branch. Cases expose stable kind and metadata for shared handling. Public
projection changes only internal message/code and preserves request identifiers.
No original exception is captured, logged, or serialized by the kernel.

Invalid field dictionaries remain isolated when their caller or an extracted
dictionary is mutated. RetryAfter's failable initializer rejects negative
milliseconds. Expected absence uses an optional successful value.

## Verification and limits

Observed 2026-10-08 with Swift 6.2.3. Seven Swift Testing tests passed; they
consume the same 13 failure cases as Kotlin. The standalone throwing-map
example failed compilation with the expected non-throwing callback diagnostic.
The unsigned iOS simulator consumer build also succeeded. The app was not launched.

From the repository root:

```sh
make kernel-test
make ios-build
```

Host tests establish the stated value behavior. They do not establish native
network cancellation, UI presentation, serialization, or device behavior.
The fixture loader locates canonical data relative to the test source path;
the library and app do not depend on the checkout filesystem at runtime.

## Next reading questions

- Why does required case data prevent an invalid failure without a fields map?
- Why can a successful nil coexist with a not-found failure?
- Which future adapter catches known dependency errors while letting defects
  and structured cancellation propagate?
