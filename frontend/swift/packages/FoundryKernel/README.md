# Swift kernel

The kernel implements typed outcomes and failures without UI, HTTP, provider,
or persistence dependencies. [The shared contract](../../../../contracts/behavior/kernel.md)
defines its behavior; [the walkthrough](../../notes/modules/packages/FoundryKernel/README.md)
explains the source and tests.

`AppResult<Value>` aliases standard `Result<Value, Failure>`. Failure enum cases
require the metadata and payload relevant to their kind. `publicInfo()` redacts
internal message/code and retains request identifiers. `RetryAfter` admits
nonnegative signed 64-bit milliseconds through a failable initializer.

From the repository root:

```sh
swift test --package-path frontend/swift/packages/FoundryKernel
```

Tests read the canonical repository fixtures. No fixture resource is packaged
with the library or app. `Examples/ThrowingMap.swift` intentionally fails
typechecking and is excluded from package targets; see the language note linked
from the walkthrough before running it.
