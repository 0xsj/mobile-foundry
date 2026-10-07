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
App icon metadata currently has no artwork.

## Used in and related

Use this split for an app shell with independently reusable packages. Revisit
this note when the toolchain, project generator, deployment baseline, or package
distribution changes. Framework state and rendering lifecycles are separate
subjects to record when their implementations begin.
