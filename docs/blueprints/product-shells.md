# Product shells construction

Authority: [product shell behavior](../../contracts/behavior/product-shells.md),
[architecture](../../ARCHITECTURE.md), [UI components](../COMPONENTS.md) and
[starter readiness](../STARTER-READINESS.md).

| Owner / files | Symbols and responsibility |
| --- | --- |
| Swift Sources/Shell/Patterns/ProductShellState.swift; Kotlin ui/shell/patterns/ProductShellState.kt | Profile and fixture identities, pure admitted actions and independent page values; bounded layers and guided progression. |
| Swift ProductShellCatalogView.swift; Kotlin ProductShellCatalogScreen.kt | Gallery/preview lifetime, per-profile state, native drafts, theme controls, close/back policy and bounded full-screen host. |
| Swift ProductShellLayouts.swift; Kotlin ProductShellLayouts.kt | Daily native tabs, adaptive workspace, browse/detail with persistent actions, canvas tools/inspector and guided flow. |
| Existing AppShell / DetailShell / AuthShell / TabBar and field/card/navigation primitives | Passive composition, native interaction and theme projection. |
| Catalog navigation entries | Product shells route on both platforms. |
| Shared shell fixture; native owner/layout/UI tests | Identity admission, profile independence, retained drafts/state, geometry/back behavior and actual interactions. |
| Native/shared notes and readiness record | Explain ownership, adaptation, evidence and remaining independent-extraction gate. |

Construction order: admit profile/item/action identities; specify independent
fixture state; compose five layouts using existing primitives; retain state and
native drafts above preview/theme branches; define available-width adaptation;
wire the catalog; verify shared transitions plus native compact/expanded layout,
back paths, recreation and form interactions; update reading step 45 and readiness.
No generator or connected product is part of this slice.
