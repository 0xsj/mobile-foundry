# iOS catalog app

This SwiftUI application hosts the native component catalog and future graphics
examples. It links the local `FoundryKernel` package.

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
