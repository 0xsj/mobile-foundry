# Async UI package walkthrough

FoundryUI renders query state through native SwiftUI rows, caller copy and a
domain content builder, without owning requests or services.

## Origin and reading order

Extracted 2026-10-08 using Xcode 26.2 and Swift 6.2.3. Read
[the contract](../../../../../../contracts/behavior/query-ui.md),
[manifest](../../../../packages/FoundryUI/Package.swift), and
[QueryContent](../../../../packages/FoundryUI/Sources/FoundryUI/QueryContent.swift).
Then compare [NotesScreen](../../../../apps/FoundryCatalog/Sources/Notes/NotesScreen.swift)
with [QueryCatalogView](../../../../apps/FoundryCatalog/Sources/Query/QueryCatalogView.swift).

## Walkthrough and gotchas

QueryCopy carries operation-specific strings and action labels. QueryContent
switches on state, labels progress as loading or refreshing, and renders a
retained snapshot through either empty copy or the content builder. Failed
state projects publicInfo before presenting its message. Buttons forward
callbacks; construction and state changes invoke no action.

The notes feature supplies note rows and list emptiness. The gallery supplies
one string and string emptiness. Group emits rows inside the host's Section;
the component introduces no nested List or task. The host still owns scrolling,
theme, observation, and lifecycle. Copy can be localized before injection.
See [content builder mechanics](../../../language/swift-generic-query-state-and-content-builders.md).

## Verification and limits

`make ios-test` compiled the package and catalog and passed seven store tests.
Simulator interactions on iPhone 17 Pro/iOS 26.2 confirmed idle/initial loading,
content/empty, refreshing both snapshots, failure retaining both snapshots,
generic internal failure copy, Retry, and Cancel restoration. Notes still showed
both content rows through the extracted component. A current screenshot showed
the populated gallery's native List layout; screenshot output was usable in
this slice, unlike the earlier notes session.

These are semantic/manual presentation checks, not automated SwiftUI rendering
tests or a VoiceOver, large-text, dark-mode, and physical-device audit. Android
instrumentation independently covers the shared presentation matrix. Tokens,
forms, and additional control patterns remain future work.

## Next questions

Which failure needs a feature-specific recovery action beyond Retry? Which
layout choices belong to the host rather than QueryContent? Read
[the shared pattern](../../../../../../notes/patterns/query-state-and-rendering.md).
