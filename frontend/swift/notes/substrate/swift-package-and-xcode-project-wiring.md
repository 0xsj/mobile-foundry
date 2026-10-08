# Swift package and Xcode project wiring

Swift Package Manager defines the reusable library, while XcodeGen defines
the iOS application target that consumes it.

## Origin

Observed during initialization on 2026-10-07 with Xcode 26.2, Swift 6.2.3, and
XcodeGen 2.46.0. Configuration and simulator builds passed in that environment.
Primary references: [Swift library initialization](https://www.swift.org/getting-started/library-swiftpm/)
and [XcodeGen project configuration](https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md).

## What and why

The package manifest exports a library product. The application configuration
declares a local package path and attaches that product to its application
target. This makes the catalog consume the same library boundary that a future
product app can use.

`project.yml` owns target settings and dependency links. XcodeGen generated the
Xcode project and shared scheme from it. Keep configuration changes there so
regeneration preserves them. The generated project can be opened directly.

The initial iOS deployment target is 17.0. The package's tools-version is 6.2;
the app uses Swift language mode 6.0. Those numbers express different settings,
and neither is an iOS deployment version.

## Example

From the repository root, after changing app configuration:

```sh
make ios-generate
make ios-build
```

During initialization, project generation and the unsigned simulator Debug
build both succeeded. Module notes connect these commands to the actual files.

## Gotchas

Simulator compilation and packaging do not establish device signing or live
interaction. The local package link is useful during foundry development;
an exported product template will need its own dependency distribution choice.
The initial icon slot had no artwork; the 2026-10-08 asset slice populates it.

## Used in and related

Use this split for an app shell with independently reusable packages. Revisit
this note when the toolchain, project generator, deployment baseline, or package
distribution changes. Framework state and rendering lifecycles are separate
subjects to record when their implementations begin.

## Generated app icon

Claim: keep original artwork and provenance separate from the app's selected,
size-constrained derivative, and let the asset catalog own platform packaging.

Added 2026-10-08 on Xcode 26.2 with the existing iOS 17 deployment target.
The built-in imagegen tool produced a 1254-square opaque RGB PNG. The original
is retained in shared assets; macOS sips downsamples a 1024-square derivative.
The AppIcon Contents.json entry selects that filename using the existing
universal iOS slot. Xcode compiles its required platform representations.
Apple documents the single-image route in
[asset-catalog app icons](https://developer.apple.com/documentation/xcode/configuring-your-app-icon).

Read the [asset manifest](../../../../assets/manifests/mobile-foundry-icon.json)
for the complete prompt, exact hashes and conversion command, then
[asset ownership](../../../../assets/README.md#mobile-foundry-app-icon) and
[the catalog metadata](../../apps/FoundryCatalog/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json).
The build setting already selects AppIcon; resource metadata changes need no
new Swift source or runtime icon selection. Preserve square outer corners and
an opaque default background instead of baking the system mask into artwork.

Observed verification: the original and derivative were visually inspected;
sips reports a 1024 × 1024 RGB image with no alpha; assets-check validates both
hashes, PNG headers, bundled bytes and selection. The unsigned simulator build
packages the new icon successfully. This does not establish signed-device
distribution or App Store Connect validation. Separate dark/tinted artwork and
layered Icon Composer treatment remain optional future work.
