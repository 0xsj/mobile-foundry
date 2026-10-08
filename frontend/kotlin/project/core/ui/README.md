# UI core

Android Compose `QueryContent` renders generic query state with caller copy,
emptiness, a content slot, and callbacks. Its host owns scrolling, observation,
and work. FoundryTheme supplies V1 tokens and maps them into MaterialTheme.
Sources follow styles/tokens, styles/presets, theme, and
components/feedback/query; see [Styles](../../../../../STYLES.md).
Forms/TextField and Forms/SubmitButton wrap native controls; Feedback/Mutation
renders write progress, public failures and caller success content. Features own
validation, focus policy and execution. See [forms behavior](../../../../../contracts/behavior/forms-mutations.md).

See [the contract](../../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../../notes/modules/project/core/ui/README.md).
Build with `make android-build`; `make android-ui-test` includes its instrumented
presentation checks in the catalog app. `make ui-test` checks shared token
fixtures, contrast, and reduction. See [the token contract](../../../../../contracts/behavior/ui-tokens.md).
Open **Tokens**, **Async UI patterns**, **Notes service seam**, or **Forms and mutations**.

FoundryTheme also selects Solid/Glass surface styles. `Surface` keeps
content panels opaque; floating controls sample the host's `Backdrop`
on API 31+, with an opaque fallback. See [surface themes](../../../../../STYLES.md#swappable-surface-themes).

See [the component map](../../../../../docs/COMPONENTS.md) for implemented and reserved
catalog folders. Three reusable batches add 27 controls and compositions, including
native selection and overlays; see [usage](../../../../../docs/blueprints/ui-components.md).
Navigation/TabBar's Kotlin counterpart
is implemented as TabBar: items, selection and a callback over the
floating surface. Route identity and presentation lifetime stay in the app.
