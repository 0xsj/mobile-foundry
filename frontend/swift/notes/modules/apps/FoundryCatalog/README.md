# Swift catalog shell walkthrough

The app composes a static SwiftUI catalog screen and links a local kernel
package, establishing the build boundary for future reusable capabilities.

## Origin

The initialized source and build configuration, inspected 2026-10-07 with
Xcode 26.2 and Swift 6.2.3. This note mirrors `apps/FoundryCatalog/`.
[Architecture](../../../../../../ARCHITECTURE.md) describes the intended
capability boundaries; the app currently implements only its shell.

## Reading order

1. [project.yml](../../../../apps/FoundryCatalog/project.yml): target, deployment
   baseline, local package dependency, and shared scheme.
2. [FoundryCatalogApp.swift](../../../../apps/FoundryCatalog/Sources/FoundryCatalogApp.swift):
   application entry and root view composition.
3. [CatalogView.swift](../../../../apps/FoundryCatalog/Sources/CatalogView.swift):
   the list of planned foundation and graphics areas.
4. [Kernel manifest](../../../../packages/FoundryKernel/Package.swift) and
   [kernel source](../../../../packages/FoundryKernel/Sources/FoundryKernel/FoundryKernel.swift):
   the imported library product and its empty source scaffold.

## Walkthrough

`FoundryCatalogApp` is the `@main` entry. Its `WindowGroup` supplies `CatalogView`
as the window's content. The separate root view keeps application composition
apart from the screen's layout.

`CatalogView` conforms to `View`. Its `body` returns `some View`, explained in
[the opaque type note](../../../language/swift-protocols-and-opaque-return-types.md).
The body places a `List` inside a `NavigationStack`, with Foundation and
Graphics sections. The final section explains that examples will be added.

The rows are `Label` values: there are no destination views, buttons, service
calls, or rendering surfaces in this screen. The `#Preview` declaration creates
the root view for previewing; a successful simulator build does not establish
that an Xcode preview was executed.

The app imports `FoundryKernel`, and `project.yml` links its library product
from `../../packages/FoundryKernel`. No kernel operations are called yet. This
is the dependency direction future catalog examples should preserve: app
composition consumes reusable packages.

## Verification and limits

During initialization on 2026-10-07, the following root commands' underlying
build operations passed:

```sh
make kernel-build
make ios-build
```

The package compiled and the iOS simulator application was produced. This
establishes build wiring, source compilation, and packaging. The app was not
launched on a simulator or device, and no UI interaction or GPU behavior was
verified. The kernel's generated test is still a placeholder.

## Questions for the next session

- Which file changes to add a destination, and which changes to add a reusable
  package dependency?
- Why is importing a package different from demonstrating its behavior?
- What state and callbacks would the first interactive catalog example need?

## Related

[Build wiring](../../../substrate/swift-package-and-xcode-project-wiring.md),
[Swift reading order](../../../README.md), and
[native setup](../../../../../../docs/SETUP.md).
