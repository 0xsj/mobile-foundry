# Workspace and Browse starters

The CLI selects `native-workspace` or `native-browse` on Swift, Kotlin or both.
They include UI → Query → Kernel and app-owned placeholder navigation. Daily
remains the CLI default. `local-data` is registered only for Daily; selecting it
with either new shell is refused before writing output.

Workspace admits three stable placeholder project IDs. Compact layout initially
shows the list; selection opens detail and Back returns to the list while keeping
the selected project. At 700 available points/dp or wider, list and detail appear
together, with an empty detail prompt before selection. Native large-text policy
prefers one pane (Swift accessibility text sizes; Android font scale >= 1.5).
Selection lives above layout branches and survives width/material changes.
Android system Back returns from compact detail before exiting the application.

Browse admits three stable placeholder collection IDs. Search filters their
titles locally, ignoring case and surrounding whitespace; empty results have an
explicit message. Selecting a result opens its detail. Back returns to the same
search, including via Android system Back. Search focus ends when opening detail.
Search, clear and detail navigation are UI examples, with no stored collection or
provider operation.

Both profiles display configured product branding and expose a Glass switch in
their fixed header. Body regions scroll within a bounded shell. Android consumes
safe drawing/keyboard insets once. Swift respects native safe areas. Invalid
selection IDs are no-ops. Swift keeps values for the window lifetime; Android
saves navigation, search and theme through Activity recreation. Fresh launches
start with defaults; durable preferences, deep links and multi-level back stacks
require further capabilities.

Each profile has an explicit complete inventory and effective build overlays;
only its app sources/tests and shared dependency closure are copied. Generated
guides describe the selected shell. Catalog fixtures, Daily screens, session,
data adapters, camera and graphics are not required by these profiles.
Standalone readiness requires renamed independent outputs, native navigation/
theme tests, compact/expanded Workspace execution and Android recreation checks.
Release signing and physical-device performance remain separate validation.
