# Shared native contracts

- `http`: Public wire specifications for owned APIs.
- `behavior`: Platform-independent behavioral specifications for kernel,
  transport, session, sync, and graphics boundaries.
- `fixtures`: Portable inputs, expected outcomes, and failure scenarios that
  both Swift and Kotlin implementations can consume.

Keep application behavior separate from backend wire details. Managed SDKs
may use different wire protocols while satisfying an application's repository
or session interface.

Generated Swift and Kotlin wire models live inside their platform service
boundaries. The canonical specification and generator inputs live here.
[Kernel outcomes and failures](behavior/kernel.md) is the first behavioral
contract, with [canonical failure fixtures](fixtures/kernel/failures.tsv)
consumed by both native test suites. HTTP specifications remain reserved for
the first owned transport or API workflow.
