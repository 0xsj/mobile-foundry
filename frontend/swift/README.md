# Swift foundation

`apps` holds the iOS catalog application and its feature examples. `packages`
holds reusable Swift Package Manager libraries. The catalog imports the same
library products that future applications will use.

`packages/FoundryKernel` and the `apps/FoundryCatalog` Xcode app are initialized
and linked. The kernel implements typed outcomes, failures, and public
projection under [its shared contract](../../contracts/behavior/kernel.md);
the app shows planned catalog areas. Build and open them using
[Setup](../../docs/SETUP.md).

The app's `project.yml` is its XcodeGen configuration. Regenerate the Xcode
project after changing targets, package dependencies, or build settings; keep
the generated project and shared scheme versioned.

Each package owns its manifest, source, tests, and any resources it compiles.
Keep platform shader code with its rendering package. Use shared graphics
fixtures from the root `assets` directory through an explicit resource-loading
or bundling step; runtime code must not depend on the checkout's filesystem path.

Package responsibility names and dependency direction are documented in
[Organization](../../docs/ORGANIZATION.md).

[Learning notes](notes/README.md) provide the source reading order, Swift
mechanics, framework findings, and verification limits for implemented slices.
