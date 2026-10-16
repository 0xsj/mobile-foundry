# Kotlin product entry

A clean Android Daily shell with Today, Library, Activity and Account. The
module depends on core/ui and its Query/Kernel closure. The catalog is `:app`.

From this folder, with a Gradle 9.1-compatible launcher JDK (17–25) selected
and the Android SDK configured; compilation uses the JDK 17 toolchain:

```sh
../gradlew -p .. :product:assembleDebug
../gradlew -p .. :product:connectedDebugAndroidTest
```

Run the second command with an emulator/device attached. Verified tool versions
are in `../gradle/libs.versions.toml`: AGP 9.0.1, Kotlin 2.3.20 and Compose BOM
2026.03.01. Compile/target SDK is 36, minimum SDK 24.

Edit [product.properties](product.properties) for installed application ID and
versions; edit [strings.xml](src/main/res/values/strings.xml) for display name.
The Kotlin/R namespace `dev.mobilefoundry.product` can stay stable when the
installed ID changes. Edit
[ProductConfiguration](src/main/kotlin/dev/mobilefoundry/product/ProductConfiguration.kt)
for initial material and tab metadata, and
[ProductRoot](src/main/kotlin/dev/mobilefoundry/product/ProductRoot.kt) for routing.
Replace the separate files under the product's `screens` package with features.

Account toggles Solid/Glass. Selection and preference survive Activity recreation
through native saved state; fresh-launch persistence is not implemented.
The app owns outline icons and a simple vector launcher placeholder. Replace
branding before distribution. It requests no camera/internet permission and has
no credential namespace or release signing configuration.

This in-repository entry builds independently of the catalog module. CLI
generation and renamed Kotlin-only/both apps are verified outside the checkout.
Read [CLI usage](../../../../cli/README.md) to create a new product.
