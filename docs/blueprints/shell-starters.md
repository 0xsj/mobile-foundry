# Native shell starters construction specification

## Status and scope

Started 2026-10-10; completed 2026-10-11 after independent native acceptance.
Extract Workspace and Browse/detail as UI-only CLI profiles. Established inputs
are [shell behavior](../../contracts/behavior/shell-starters.md),
[generation behavior](../../contracts/behavior/project-cli.md),
[product entry](../../contracts/behavior/product-entry.md) and
[architecture](../../ARCHITECTURE.md). Catalog compositions provide a layout
precedent; their fixture actions are not product requirements.

## Exemplar workflow

A developer generates a renamed native project, selects a placeholder project
or filtered collection, opens detail, changes material and returns. Workspace
also reflows between compact and expanded panes without losing selection.
Both native platforms must work independently of the source checkout.

## Nouns, states, and invariants

Product configuration owns branding/default material. Each shell owns a typed,
stable identity list and ProductState. Workspace owns selected identity and a
compact detail flag; Browse owns selected identity and search text. Unknown
identities do not change state. Material and navigation outlive presentation
branches. No persistence, transport or domain service belongs to this slice.

## Workflow and side-effect ordering

CLI admission and inventory validation precede exclusive output creation.
Profile-specific sources replace app presentation/state; shared libraries remain
canonical. Identity configuration and shell-specific guides follow copying.
In the app, selection is admitted before detail presentation; Browse releases
search focus before leaving the list. Back changes presentation without resetting
Workspace selection or Browse search. Android state restoration precedes layout.

## Expected file tree and file specifications

All following files are required. Each overlay maps to the existing ProductApp
target or product module, preserving native dependency/fixture depth.

| Owner / files | Symbols, responsibilities and verification |
| --- | --- |
| `templates/profiles/native-workspace.json`, `native-browse.json` | Complete inventories: only UI/Query/Kernel closure, exact app sources and selected shell overlays. Required app files catch omissions/exclusions. CLI copy and drift tests verify them. |
| `templates/overlays/shells/swift/project.yml` | ProductApp application, hosted tests and UI test target. No data/session/graphics package links. Independently generated build/test verifies wiring. |
| `templates/overlays/shells/swift/ProductConfiguration.swift`; Kotlin equivalent | Runtime branding/default material only. Metadata/permission checks verify product isolation. |
| Swift `ProductEntryTests.swift`; Kotlin equivalent | Branding and permission isolation using actual installed metadata. |
| `templates/overlays/workspace/swift/{ProductState,ProductRootView}.swift` | ProductProject identities; select/back admission; bounded themed AppShell and fixed header. Invalid-ID and actual navigation tests verify ownership. |
| Swift `WorkspaceScreen.swift` | Passive SplitPane slots with app-owned state; list selection, detail placeholder, compact Back, empty expanded detail. Native phone/tablet tests verify geometry and retention. |
| Swift `WorkspaceStateTests.swift`, `WorkspaceUITests.swift` | Admission/back/material state tests plus actual list/detail interactions, theme change, width transition and screenshots. |
| `templates/overlays/workspace/kotlin/{ProductState,ProductRoot,WorkspaceScreen}.kt` | Stable identities, explicit saveable state above theme/layout, system Back only in compact detail. No ViewModel/provider required for placeholders. |
| Kotlin `WorkspaceScreenTest.kt` | Actual Activity navigation/recreation; bounded expanded and large-text compositions; geometry transition without selection loss. |
| `templates/overlays/browse/swift/{ProductState,ProductRootView}.swift` | ProductCollection identities; local filtering/select/back; themed bounded root and fixed header. Search state remains outside screen branch. |
| Swift `BrowseScreen.swift` | Search/clear/empty results and placeholder detail with explicit Back. Focus ends on selection. |
| Swift `BrowseStateTests.swift`, `BrowseUITests.swift` | Unknown selection, normalization/filter retention; actual search/clear/detail/theme navigation and screenshots. |
| `templates/overlays/browse/kotlin/{ProductState,ProductRoot,BrowseScreen}.kt` | Same behaviors with saveable search/selection/material, focus release and Android system Back. |
| Kotlin `BrowseScreenTest.kt` | Actual filtering, empty/clear, detail/theme and recreation/back interactions. |
| `mobile-foundry.starters.json`, CLI version/selection/profiles/documents and `cli/tests/test_shells.py` | Explicit registrations, supported-shell admission, default Daily compatibility, selected-shell guides and independent native source copies. Verify all six new platform combinations and incompatible local-data refusal. |
| Contracts, CLI/template/readiness guides, shared/native note indexes and product walkthroughs | Scope, evidence and learning handoff. Notes checks validate links; native runs establish readiness. |

## Cross-file dependency map

CLI registry → inventory → generation → selected app overlays and canonical UI →
Query → Kernel. Product entry → configuration/root/state → selected shell screen
→ passive shell/field/card components. Native tests depend on the generated app.
Other shell sources, catalog owners and backend providers are forbidden dependencies.

## Transport and persistence mapping

Not applicable. Kotlin Activity saved state is temporary UI restoration, not a
durable database or authentication session.

## Verification plan

Generate Swift-only, Kotlin-only and combined renamed outputs for each profile
outside the checkout. Verify source closure, branding, compatible choices and
guide commands. Build both platforms; run generated Swift hosted/UI tests on
phone and Workspace on tablet; run Android real-Activity and bounded-pane tests.
Inspect launch/navigation screenshots. Shared library checks are unchanged;
run them once for the selected closure. Check notes and whitespace.

## Construction order

Contract/plan; identity/state/presentation overlays; explicit inventories; CLI
registration/default/guides and copy tests; generate independent outputs; native
verification and correction; update readiness and asynchronous learning notes.

## Open decisions and unknowns

Deep links, real records, local-data combinations, Canvas/Setup extraction and
backends are deferred. No external documentation change is needed to use the
already implemented passive UI APIs.

## Deviation record

| Location | Finding / actual behavior | Reason | Decision needed? |
| --- | --- | --- | --- |
| Generated Swift UI tests | Tap the switch at its trailing control; omit container-level action identifiers. | Native accessibility exposes a full labeled row and can propagate container identifiers to descendant buttons. Initial center/Back locators failed; corrected tests preserve the specified behavior. | No |
| Generated Android pane test | Scope duplicate project-title assertions to detail. | Expanded layout intentionally contains the same title in list and detail. | No |
| Native verification environment | Use a bounded tool PATH; restart stalled verification processes. | Android shell startup and Xcode's temporary package executable stalled before source execution; a manifest sample showed dyld startup. Retried native builds establish source behavior separately from that host wait. | No |

The shared root files are reused across the two inventories; shell-specific
ProductState/ProductContent retain separate owners. No behavioral scope change.

## Completion criteria

Both profiles generate independently for all supported platform choices; native
navigation/material/recreation and Workspace adaptation pass; guides and notes
describe actual scope and evidence. Only then promote inventories to standalone.

Completed: six fresh platform/profile copies build/run; four iOS outputs pass
three cases each, Workspace Android outputs pass four and Browse three each.
Swift shared checks pass nineteen, Kotlin twenty; CLI twenty-two. Captures were
inspected. Inventories were promoted after these checks; [readiness](../STARTER-READINESS.md)
records toolchains, artifacts and limits.
