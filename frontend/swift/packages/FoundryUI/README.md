# FoundryUI

SwiftUI `QueryContent` renders generic query state using injected copy,
an emptiness predicate, a content builder, and user-action callbacks. The host
owns layout, observation, and asynchronous work. Initial scope is async
presentation; tokens, forms, and a broader control system remain future slices.

See [the contract](../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../notes/modules/packages/FoundryUI/README.md).
`make ios-test` compiles the real consumer and runs its state-owner regressions.
Open **Async UI patterns** or **Notes service seam** in the catalog.
