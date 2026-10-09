# Search projections and filter drafts

Claim: visible search results are a projection of feature values; editing filters
and remembering queries need their own explicit admission points.

Origin/evidence, 2026-10-09: the Discovery UI batch implements local search,
recent queries, independent saved/open actions and native filter drafts on both
platforms. Source and native checks distinguish presentation from committed
feature values. The example is a small library fixture, not a search service.

## What and why

A search term and applied facets derive visible records. They do not own those
records or their identity. Keep saved IDs and the last-opened ID separately;
filtering to zero results should not silently erase that state. Actions should
check current availability and membership again before dispatching. UI disabled
state helps interaction, but it is not domain or server authorization.

A filter sheet is an editing session. Copy applied values when opening it,
change a draft, and commit only on Apply. Reset draft, Discard, outside/native
dismissal and restoring the screen have distinct meanings. Restoring applied
values does not mean restoring an outstanding modal intent. Removing an applied
chip changes one facet without resetting unrelated filters.

History has another admission policy: remember a submitted nonblank term or an
explicit suggestion/open action, not every keystroke. Trim, deduplicate and bound
history in the feature. Clearing the field need not erase recent queries.
Production privacy, account scoping, persistence and deletion remain decisions
for a real consumer.

## Example and gotchas

Save Motion study, open it, then apply Writing while searching motion. The
projection is empty, but saved count and last-opened title remain. A hidden
record cannot be opened or toggled through a stale fixture action. Editing a
Design filter and discarding keeps Writing applied; resetting the draft and
restoring Android state also keeps Writing applied without reopening the sheet.

Reusable highlighted text accepts complete literal runs, avoiding cross-platform
range/offset contracts. It preserves characters and does not parse markup.
The fixture styles one case-insensitive literal match; accent folding, grapheme
boundaries, locale-aware equivalence, ranking and full-text indexing need explicit
service/feature policy. Equal visual emphasis is not proof of equal multilingual
search behavior. Keep complete accessible open copy when hiding passive previews.

## Actual use and next questions

Read [Swift Discovery flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#discovery-gallery),
[Kotlin Discovery flow](../../frontend/kotlin/notes/modules/project/app/README.md#discovery-gallery)
and [component usage](../../docs/blueprints/ui-components.md#search-and-discovery).
Related: [selection identity and undo](selection-identity-and-undo.md) and
[transport and service boundaries](transport-service-and-screen.md).
Next: connect a real query owner with cancellation/stale-result rules, decide
account-scoped history/privacy, and test locale matching before claiming a
production search experience. No full assistive-technology or device audit is
established by this batch.
