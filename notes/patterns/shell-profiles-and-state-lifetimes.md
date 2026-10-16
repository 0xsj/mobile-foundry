# Shell profiles and state lifetimes

Claim: a shell profile chooses a product's navigation composition, while its
state owner decides what survives a presentation change.

## Origin and evidence

Added 2026-10-10 while extracting Workspace and Browse from native catalog
compositions into independent CLI products. Behavior comes from
[shell starters](../../contracts/behavior/shell-starters.md), construction from
[the blueprint](../../docs/blueprints/shell-starters.md), and observed acceptance
from [readiness](../../docs/STARTER-READINESS.md). This is a source/verification
finding, not a platform guarantee about every navigation framework.

## What and why

A shell is an app composition rather than a dependency provider. Workspace has
two pieces of navigation state: the selected project and whether compact detail
is presented. Back hides detail while retaining the selection. Expanded layout
can still display that selected overview. Browse instead clears detail selection
on Back while retaining the search. One universal selected-screen string would
hide this difference.

Material and available-width branches can reconstruct presentation. Put values
that must survive those changes above the branches. Shared SplitPane owns width
and native text-size policy; the app owns selected identities and Back. The same
separation makes a placeholder easy to replace with a real feature later.

## Example

| Change | Workspace owner retains | Browse owner retains |
| --- | --- | --- |
| Glass to Solid | Project identity and compact detail flag | Collection identity and search |
| Compact Back | Project identity; clears detail flag | Search; clears collection identity |
| Expanded layout | Selected project, or empty detail before selection | No split layout is selected in this profile |
| Android Activity recreation | Encoded identity, detail flag and material | Encoded identity, search and material |
| Fresh launch | Defaults | Defaults |

These are placeholder app values. Restoring a UI selection does not establish
that a remote record exists or that a credential is still valid. A real feature
must admit restored identity through its current domain/service boundary.

## Gotchas

- Copy only the selected shell's sources. A runtime gallery chooser would make
  every product carry unrelated screens and fixture actions.
- Explicit inventories protect source closure as well as build dependencies.
  Required state/screen/test files must not disappear through exclusions.
- Multiple profiles should not silently change an existing CLI default. Daily
  remains explicit; `--profile` selects Workspace or Browse.
- A new shell does not make existing capabilities compatible automatically.
  Local-data remains a registered Daily combination until another composition
  and its acceptance checks are implemented.
- Scope test locators to the intended pane when titles appear twice. On SwiftUI,
  attaching an identifier to a layout container can propagate it to descendants;
  the generated tests identify actionable buttons directly. Hit the native switch
  rather than assuming the center of its full labeled accessibility row is active.

## Used in and related

Native module walkthroughs describe their selected overlays and actual checks:
[Swift ProductApp](../../frontend/swift/notes/modules/apps/ProductApp/README.md#workspace-and-browse-output)
and [Kotlin product](../../frontend/kotlin/notes/modules/project/product/README.md#workspace-and-browse-output).
Read [shell ownership](product-shells-and-navigation-ownership.md),
[extraction closure](product-entry-and-extraction-closure.md) and
[explicit capabilities](../techniques/explicit-capabilities-and-effective-build-graphs.md).

Next: when replacing placeholders with records, which owner validates a restored
identity, and which owner handles a detail that has since been deleted?
