# FoundryUI

SwiftUI `QueryContent` renders generic query state using injected copy,
an emptiness predicate, a content builder, and user-action callbacks. The host
owns layout, observation, and asynchronous work. The package also owns V1 semantic
tokens and a scoped SwiftUI FoundryTheme. Sources follow Styles/Tokens,
Styles/Presets, Theme, and Components/Feedback/Query; see [Styles](../../../../STYLES.md).
Forms/TextField and Forms/SubmitButton wrap native controls; Feedback/Mutation
renders write progress, public failures and caller success content. Features own
validation, focus policy and execution. See [forms behavior](../../../../contracts/behavior/forms-mutations.md).

See [the contract](../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../notes/modules/packages/FoundryUI/README.md).
`make ios-test` compiles the real consumer and runs its state-owner regressions.
`make ui-test` checks shared token fixtures, contrast, and reduced motion on both
platforms. See [the token contract](../../../../contracts/behavior/ui-tokens.md).
Open **Tokens**, **Async UI patterns**, **Notes service seam**, or **Forms and mutations** in the catalog.

FoundryTheme also selects Solid/Glass surface styles. `FoundrySurface` keeps
content panels opaque and adapts floating controls to native glass/material,
with transparency reduction. See [surface themes](../../../../STYLES.md#swappable-surface-themes).

See [the component map](../../../../docs/COMPONENTS.md) for the reserved empty
catalog folders and intended contents. The four-tab prototype lives in app
composition and uses native TabView rather than a reusable routing wrapper.
