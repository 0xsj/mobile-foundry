# Swift product entry

A clean iOS Daily shell with Today, Library, Activity and Account. It depends
only on FoundryUI and its Query/Kernel closure. The catalog is a separate app.

From this folder:

```sh
xcodegen generate
xcodebuild -project ProductApp.xcodeproj -scheme ProductApp \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .build/ios CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build
xcodebuild -project ProductApp.xcodeproj -scheme ProductApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath .build/ios CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

Choose an installed simulator for the test destination. XcodeGen 2.46+,
Swift 6.2 and Xcode 26.2 are the verified tools; minimum deployment is iOS 17.

Edit [Product.xcconfig](Config/Product.xcconfig) for display name, bundle ID,
versions and your signing team. Keep the source target/module/scheme `ProductApp`
stable unless deliberately renaming code. Edit
[ProductConfiguration](Sources/ProductConfiguration.swift) for default material
and tab metadata; [ProductRootView](Sources/ProductRootView.swift) owns routing.
Replace the separate files under `Sources/Screens` with product features.

Account toggles Solid/Glass. State lives above the theme subtree, lasts for the
window session and is not persisted to preferences. Native tab chrome follows
iOS; the toggle changes reusable floating panels. The app has no camera/network
feature or credential namespace. Add product branding/AppIcon before distribution.

This in-repository entry builds independently of the catalog target. CLI
generation and renamed Swift-only/both apps are verified outside the checkout.
Read [CLI usage](../../../../cli/README.md) to create a new product.
