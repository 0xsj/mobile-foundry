# UI core

Android Compose `QueryContent` renders generic query state with caller copy,
emptiness, a content slot, and callbacks. Its host owns scrolling, MaterialTheme,
observation, and work. Initial scope is async presentation; tokens, forms, and
a broader control system remain future slices.

See [the contract](../../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../../notes/modules/project/core/ui/README.md).
Build with `make android-build`; `make android-ui-test` includes its instrumented
presentation checks in the catalog app. Open **Async UI patterns** or **Notes
service seam**.
