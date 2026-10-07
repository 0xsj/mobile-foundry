# Native project setup

The SwiftUI iOS app, Compose Android app, Swift kernel package, and Kotlin/JVM
kernel module are initialized. Do not rerun project initialization commands in
these existing directories.

This machine has Xcode 26.2, Swift 6.2.3, Android Studio, the Android SDK, and
XcodeGen 2.46.0. On a new Mac, install Xcode and Android Studio, select Xcode's
command-line tools, and install XcodeGen with `brew install xcodegen`.

## Build shortcuts

Run from the repository root:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry
make kernel-build
make ios-build
make android-build
make android-test
make notes-check
```

`notes-check` uses Python 3 to check documentation links, mirrored module-note
directories, and native example labels. Follow [the learning workflow](NOTES.md)
for reading orders and execution of complete examples.

The Android shortcuts default to Android Studio's bundled JDK and the SDK at
`$HOME/Library/Android/sdk`. Override them when your installation differs:

```sh
make android-build FOUNDRY_ANDROID_JAVA_HOME="/path/to/jdk" \
  FOUNDRY_ANDROID_SDK="/path/to/android-sdk"
```

## Swift kernel package

Build the initialized package directly:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/swift/packages/FoundryKernel
swift build
```

The package has source and test directories but no kernel behavior yet. Its
generated example test is a placeholder; replace it with meaningful checks as
behavior is implemented, then run `swift test` from this directory.

Create additional packages only when their first implementation is ready. For
example, from the Swift packages directory:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/swift/packages
mkdir FoundryHTTP
cd FoundryHTTP
swift package init --type library --name FoundryHTTP --enable-swift-testing
```

See [Swift library initialization](https://www.swift.org/getting-started/library-swiftpm/)
and the capability names in [Organization](ORGANIZATION.md).

## iOS catalog application

Open the initialized project:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/swift/apps/FoundryCatalog
open FoundryCatalog.xcodeproj
```

Select the shared `FoundryCatalog` scheme and an iOS simulator in Xcode, then
run the app. Choose a development team in Signing & Capabilities to run on a
physical device. The initial screen lists the planned catalog areas.

The app imports the local `FoundryKernel` package. Its initial deployment
target is iOS 17 and its bundle identifier is `dev.mobilefoundry.catalog`.
The asset catalog contains accent color and app icon metadata; add icon artwork
when the app's visual identity is defined.

`project.yml` owns the Xcode project configuration. After changing targets,
package dependencies, or build settings, regenerate from the repository root:

```sh
make ios-generate
```

Keep the generated `.xcodeproj` and shared scheme versioned so a checkout can
open in Xcode directly. See [XcodeGen project configuration](https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md).

To build directly from the application's directory:

```sh
xcodebuild -project FoundryCatalog.xcodeproj -scheme FoundryCatalog \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ../../../../.cache/ios \
  -configuration Debug CODE_SIGNING_ALLOWED=NO -quiet build
```

The simulator application is written to
`.cache/ios/Build/Products/Debug-iphonesimulator/FoundryCatalog.app` under the
repository root.

## Kotlin Android application

Open the Gradle project in Android Studio:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/kotlin/project
open -a "Android Studio" .
```

Select the `app` run configuration and an emulator or connected device. The
app retains the generated Compose starter screen and examples; the full
foundry catalog is a future implementation slice. The app already depends on
the empty Kotlin/JVM module `:core:kernel`.

For direct terminal builds on this Mac:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :core:kernel:build :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The wrapper uses Gradle 9.1.0. The version catalog pins Android Gradle Plugin
9.0.1, Kotlin 2.3.20, and Compose BOM 2026.03.01. Modules use a Java 17
toolchain. The app's minimum SDK is API 24, and its compile and target SDK are
API 36. Review these baselines when selecting graphics capabilities.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Starter unit tests
can run on the host; instrumented UI tests require an emulator or device and
are not included in the build shortcuts.

The original generator command is retained here for reference. Use it only for
a new absent or empty project directory, not the initialized `project` folder:

```sh
android create --name=FoundryCatalog \
  --application-id=dev.mobilefoundry.catalog \
  --namespace=dev.mobilefoundry.catalog \
  --output=./project
```

See [Android project creation commands](https://developer.android.com/tools/agents/android-cli/commands/create)
and [Compose setup](https://developer.android.com/develop/ui/compose/setup).

## Version control

Run Git commands from `mobile-foundry`, keeping both platforms and shared
contracts in one repository. Keep native project files, dependency locks,
Gradle wrapper files, and fixture inputs versioned. Local SDK paths,
credentials, build output, and IDE user state are covered by the root
`.gitignore`.
