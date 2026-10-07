# Swift learning notes

Read the Swift implementation through its module walkthroughs, then use the
linked language and tool notes to understand the choices. This notebook follows
[the shared workflow](../../../docs/NOTES.md) and Bento's categories.

## Reading order

1. [Swift package and Xcode project wiring](substrate/swift-package-and-xcode-project-wiring.md).
2. [Protocols and opaque return types](language/swift-protocols-and-opaque-return-types.md).
3. [Catalog shell walkthrough](modules/apps/FoundryCatalog/README.md), following
   the source links from app composition to the displayed list.
4. [Result and failure values](language/swift-result-and-failure-values.md), then
   [the kernel walkthrough](modules/packages/FoundryKernel/README.md).

## Findings by lifespan

| Directory | Subject |
| --- | --- |
| `modules/<source-path>/` | Local code flow, decisions, and verification |
| `language/` | Transferable Swift mechanics |
| `patterns/` | Architectural approaches specific to the Swift implementation |
| `concepts/` | Domain distinctions needing a Swift-specific explanation |
| `techniques/` | Swift investigation and verification methods |
| `substrate/` | Versioned Xcode, SwiftUI, SDK, package, and tool behavior |

Paths mirror the Swift root: `apps/FoundryCatalog/` maps to
`notes/modules/apps/FoundryCatalog/`. Module notes link source and checks;
transferable notes describe the mechanism without local source paths.

## Current coverage

The 2026-10-07 bootstrap links a SwiftUI app to an empty Swift kernel library.
The package and iOS simulator builds passed. The catalog shows planned areas;
it has no feature navigation, service calls, or renderer yet.

The 2026-10-08 kernel slice implements typed outcomes, immutable failures, and
public projection. Seven host tests passed against shared fixtures. The
language note includes the verified rejection of a throwing Result.map callback.
Shared reasoning is in [expected failures and diagnostics](../../../notes/concepts/expected-failures-and-diagnostics.md).
HTTP, async adapters, UI examples, and diagnostic reporting remain future slices.

## Questions for the next session

- Why can a view expose `some View` without naming the full composed type?
- Where does the app choose its root view, and where does the local package
  become a build dependency?
- Which failure kinds and codes should the first HTTP operation admit?
- Where will a native adapter distinguish dependency errors, defects, and
  structured cancellation?
