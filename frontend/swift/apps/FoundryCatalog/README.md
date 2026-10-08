# iOS catalog app

This SwiftUI application hosts the native component catalog and future graphics
examples. It links local `FoundryKernel`, `FoundryHTTP`, `FoundryServices`, `FoundryQuery`,
and `FoundryUI` packages.
Open HTTP health to try Healthy, Unavailable, Malformed, Validation, Rate limited,
and Timeout. Responses are injected; no backend is needed.

Notes service seam uses a domain port, interchangeable memory/HTTP adapters,
and an observable store. Its list renderer consumes state and callbacks only.
Try Content, Empty, Unavailable and Slow; refresh and cancellation are explicit.
Run `make ios-test` from the repository root for the store's behavior checks.

`project.yml` is the source of truth for the Xcode project configuration.
Regenerate after changing its targets, resources, settings, or package links:

```sh
xcodegen generate
```

Keep the generated project and shared scheme versioned so Xcode can open the
checkout immediately. Make configuration changes in `project.yml` before
regenerating.

The initial deployment target is iOS 17. It is a bootstrap baseline and can
be reviewed as graphics capabilities are selected. Physical-device runs need
a signing team configured locally in Xcode.
