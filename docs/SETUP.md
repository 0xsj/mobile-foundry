# Native project setup

The SwiftUI iOS app, Compose Android app, and native kernel, HTTP, services, query, UI and graphics
packages/modules are initialized. Do not rerun project initialization commands in
these existing directories.

This machine has Xcode 26.2, Swift 6.2.3, Android Studio, the Android SDK, and
XcodeGen 2.46.0. On a new Mac, install Xcode and Android Studio, select Xcode's
command-line tools, and install XcodeGen with `brew install xcodegen`.

## Build shortcuts

Run from the repository root:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry
make kernel-build
make kernel-test
make http-test
make query-test
make ui-test
make graphics-test
make ios-build
make ios-test
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

The kernel implements outcomes and failures. HTTP and services are initialized
local packages too; do not rerun `swift package init` in their directories.

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/swift/packages/FoundryHTTP
swift test
cd ../FoundryServices
swift test
```

Use `make http-test` at the repository root to run both platforms' HTTP and
health checks. See [Organization](ORGANIZATION.md) for reserved future capabilities.

## iOS catalog application

Open the initialized project:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/swift/apps/FoundryCatalog
open FoundryCatalog.xcodeproj
```

Select the shared `FoundryCatalog` scheme and an iOS simulator in Xcode, then
run the app. Choose a development team in Signing & Capabilities to run on a
physical device. Select HTTP health in the Foundation list to try the injected scenarios.
Select Notes service seam to run the same list screen against memory or an HTTP
adapter with injected responses. Select Async UI patterns to explore generic
loading, retained content/empty, failure, retry, and cancellation presentation.
Select Tokens to inspect the V1 roles and scales. Its appearance and reduced-motion
controls preview a scoped theme while keeping the catalog controls outside it.

The app links local `FoundryKernel`, `FoundryHTTP`, `FoundryServices`,
`FoundryQuery`, `FoundryUI`, and `FoundryGraphics` packages. Its initial deployment
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

`make ios-test` runs the catalog's store tests on iPhone 17 Pro by default.
Choose another installed simulator with
`make ios-test FOUNDRY_IOS_TEST_DESTINATION='platform=iOS Simulator,name=YOUR_DEVICE'`.
The unit-test target is defined in `project.yml` and the shared scheme.

## Kotlin Android application

Open the Gradle project in Android Studio:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/frontend/kotlin/project
open -a "Android Studio" .
```

Select the `app` run configuration and an emulator or connected device. The
app retains the generated starter and adds HTTP health, Notes service seam, and
Async UI patterns, Tokens, Forms and mutations, and GPU effects destinations.
Catalog scenarios need no backend. The app already depends on
the Kotlin/JVM module `:core:kernel`, which implements typed outcomes and failures.

For direct terminal builds on this Mac:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :core:kernel:build :core:http:build :core:services:build :core:query:build :core:ui:assembleDebug :core:graphics:assembleDebug :app:assembleDebug
./gradlew :core:kernel:test :core:http:test :core:services:test :core:query:test :core:ui:testDebugUnitTest :core:graphics:testDebugUnitTest :app:testDebugUnitTest
```

The wrapper uses Gradle 9.1.0. The version catalog pins Android Gradle Plugin
9.0.1, Kotlin 2.3.20, and Compose BOM 2026.03.01. Modules use a Java 17
toolchain. The app's minimum SDK is API 24, and its compile and target SDK are
API 36. Review these baselines when selecting graphics capabilities.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Starter unit tests
can run on the host; instrumented UI tests require an emulator or device and
use `make android-ui-test` with a booted emulator or connected device.

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

## Kernel behavior checks

From the repository root:

```sh
make kernel-test
```

This runs Swift package tests and Kotlin kernel host tests. Both read the
canonical failure fixtures from `contracts/fixtures/kernel`; the runtime
libraries do not load files from the checkout. `make android-test` also includes
all Kotlin core tests alongside the starter app tests. See
[the kernel contract](../contracts/behavior/kernel.md) and each platform's
notebook for examples and evidence limits.

## Image and product previews

Open **Image studio** in either catalog. Drag in Compare mode to move the
original/edited divider; change exposure, saturation and vignette, then choose
Move image to pan or pinch to zoom. Original/Edited, Zoom and Reset controls
provide native alternatives. Open **Product studio** to orbit/pinch the lamp,
choose Porcelain/Cobalt/Bronze, reset the camera or enable Turntable. Reduce motion
suppresses the turntable while controls still redraw a static scene.

Canonical assets and provenance are described in [assets](../assets/README.md).
Run `make assets-sync` after changing the authored model or source photograph,
then `make assets-check`. Regenerate the iOS project after adding resource files.
The reusable graphics modules accept admitted data instead of catalog paths.
Import/export, general model loading, saved edits and backend assets are future
slices. The small internal triangle JSON is not glTF.

`make graphics-test` now includes four value/policy checks per native platform
and two actual Metal execution checks. `make ios-test` hosts both previews to
verify drawable sizing/static updates/removal. `make android-ui-test` reads real
surface pixels and exercises image tools, product material/camera/pinch, quality,
motion reduction, context recreation and navigation. These are simulator/emulator
checks; physical-device frame-time and thermal profiling remain open.

## GPU effects

Open **GPU effects** in either catalog. Drag Ripple to move the wave center;
select Orbit to steer a lit sphere and ring. Change strength or Economy/Balanced,
disable Animate, then interact again. Reduce motion preview keeps animation
paused even with Animate enabled. Reset focus centers touch without resetting
time. Background/resume and Back/reopen exercise native resource lifetime.
No backend, external assets or credentials are required.

`make graphics-test` runs two shared policy tests per platform and a native
offscreen Metal shader/pixel test on a Metal-capable Mac. That test explicitly
skips when no Metal device is available. `make ios-test` adds a hosted canvas
dimension, automatic drawing, paused quality, resume and disposal check.
`make android-ui-test` checks actual GL surface pixels, controls, quality,
context recreation and removal on a booted emulator/device.

The iOS adapter uses Metal/MTKView; Android uses OpenGL ES 2/GLSurfaceView at the
existing API 24 floor. Shader text is bundled and compiled once per attachment
or recreated context. No new third-party engine is installed. Counters report
submissions and target pixels, not displayed FPS or GPU time. Profile physical
devices before selecting sustained performance targets. Read the
[contract](../contracts/behavior/gpu-effects.md) and
[learning handoff](../notes/patterns/renderer-frame-ownership.md).

## HTTP and health examples

`make http-test` runs Swift HTTP/services and Kotlin HTTP/services host tests.
Both use the canonical JSON cases under `contracts/fixtures/http`; native
adapter tests additionally exercise cancellation, redirects and interrupted
bodies. Swift loopback tests and Kotlin MockWebServer require local test sockets.

Both catalogs offer Healthy, Unavailable, Malformed, Validation, Rate limited,
and Timeout. Run again repeats the selected scenario. No backend or credentials
are needed. The native transport implementations can be injected into a future
connected workflow; the catalog uses deliberate fixtures.

```sh
make android-ui-test
```

This device-dependent target exercises Compose scenarios and request replacement.
See the [HTTP contract](../contracts/behavior/http.md) and
[learning notes](../notes/README.md) for ownership and evidence limits.

## Notes service and query example

Notes service seam offers Memory/HTTP and Content/Empty/Unavailable/Slow.
The HTTP option still uses the real HTTP client and notes response admission,
with catalog-only wire responses. Refresh keeps prior content while loading;
Cancel loading stops owned work. The list renderer receives domain state and
callbacks and constructs no service.

`make http-test` includes both platforms' notes service tests against shared
fixtures. `make ios-test` and `make android-test` exercise the store/ViewModel;
`make android-ui-test` also exercises provider switching and notes states.
See [the notes/query contract](../contracts/behavior/notes-query.md). No backend,
provider account, persistent storage or credentials are needed for this example.

## Reusable query state and async UI

`make query-test` runs Swift and Kotlin pure query-state tests against
`contracts/fixtures/query`. `make ios-test` and `make android-test` retain the
notes state-owner regression checks. `make android-ui-test` also checks the
generic presentation matrix, callback routing, and gallery navigation.

Open Async UI patterns in either catalog to choose idle, initial loading,
content, empty, refresh/failure retaining either snapshot, or internal failure.
Refresh/Retry enters loading; Cancel restores content or idle. This gallery
manually selects states and performs no service calls. Notes service seam
exercises the same reusable UI with real feature-owned request lifetime.
See [the query/UI contract](../contracts/behavior/query-ui.md).

## Tokens and native themes

`make ui-test` runs Swift FoundryUI and Kotlin core/ui token tests against the
shared V1 light/dark palette and scales. `make android-test` also includes the
Kotlin suite. `make android-ui-test` checks nested themes, Material mapping,
font scaling, minimum touch bounds, catalog controls, and preserved sample state.

Open Tokens and choose System, Light, or Dark; System follows the surrounding
theme. Turn on Reduce motion preview and scroll to Motion: durations become
0 / 0 / 0 ms, and Toggle position still changes the endpoint. The preview flag
can request reduction but cannot override an operating-system request. Native
widgets retain their own animation policy. Primary/Secondary action increments
the counter, which survives appearance changes.

On the catalog home, enable Glass surfaces, then open Tokens. Surface theme
defaults to App theme; choose Solid or Glass for a local comparison. Move scene
changes the backdrop, Select object increments the count, and Reduce transparency
preview makes the floating panel opaque while retaining the selected style.
The scene and count survive theme changes. Swift additionally respects system
Reduce Transparency. Android requires API 31+ for backdrop blur and falls back
to a solid surface on older supported versions. These choices last for the
catalog session; there is no saved cross-launch theme preference in this slice.

The deterministic Foundry Studio V1 preset uses porcelain/graphite surfaces and
cobalt accents; it does not select Android wallpaper colors. The app
theme delegates to the reusable FoundryTheme. Follow [Styles](../STYLES.md) for
the token/preset/theme/component split and [the contract](../contracts/behavior/ui-tokens.md)
for current behavior. Forms and mutation state remain subsequent slices.

## Backend profiles

The profiles are independent. Start whichever one the current experiment uses.
Docker Desktop must be running for the two Compose profiles.

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/backend/go
go mod init mobilefoundry/backend
```

Choose a repository import path instead if desired. This creates only `go.mod`;
the reserved Go directories contain no executable or dependencies yet. See
[Go layout](../backend/go/README.md).

Supabase requires OpenSSL and Node.js on the host for one-time key generation:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/backend/supabase
make up
make status
make down
```

The first setup generates an ignored `.env`. Studio and the API use
`http://localhost:56321`; email capture uses `http://localhost:56324`.
Read [Supabase setup](../backend/supabase/README.md) for credentials and ports.

Firebase builds its CLI and Java runtime into the emulator image:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/backend/firebase
make up
make status
make down
```

The emulator UI uses `http://localhost:4000`. Auth, Firestore, and Storage use
9099, 8080, and 9199. `make down` allows graceful export; the next start imports
the retained data volume. Read [Firebase setup](../backend/firebase/README.md)
before connecting SDKs; the initial data rules deny client access.

For native connections, iOS Simulator uses `127.0.0.1`, and Android Emulator
uses `10.0.2.2`. Physical devices need a reachable host address and an explicit
bind change. Native SDK adapters and development-only network settings will
be added with the connected workflow.

## Forms and mutations catalog

Open **Forms and mutations** in either native app. No backend is required;
Memory uses an instance-local store and HTTP injects wire responses through
the real client/service admission path. The notes domain is an example.

1. Submit an empty title: the required error appears and Title receives focus.
2. Enter a title, change provider, and confirm the draft stays present. Use the
   keyboard Done/Return or Create note; both share guarded admission.
3. Choose Field error and submit. The draft remains with a server title error.
   Editing clears server feedback and revalidates the touched field.
4. Choose Unavailable, Timeout or Malformed and submit to inspect public
   feedback and missing-confirmation copy. Switch back to Success to recover.
5. Choose Slow and submit. Field, submit, reset and provider/scenario controls
   disable during the attempt. After confirmation, New note resets the form.
6. Leave during Slow, reopen, and confirm a new form remains usable. Screen
   exit suppresses old results; it does not promise a remote rollback.

Try the home Glass surfaces switch: the form's outer panels adopt glass over a
subtle cobalt backdrop. Solid returns them to opaque surfaces; transparency
reduction and unsupported blur also retain an opaque fallback.
The write gallery does not update the separate Notes service seam gallery;
read/write composition and persistence are later capabilities. There is no
automatic retry or server idempotency guarantee.

`make http-test` includes command/provider fixtures; `make query-test` includes
mutation values. `make ios-test` and `make android-test` cover feature execution;
`make android-ui-test` covers actual Compose fields/IME/feedback. See
[the contract](../contracts/behavior/forms-mutations.md) and
[learning handoff](../notes/patterns/forms-and-mutation-ownership.md).

## Version control

Run Git commands from `mobile-foundry`, keeping both platforms and shared
contracts in one repository. Keep native project files, dependency locks,
Gradle wrapper files, and fixture inputs versioned. Local SDK paths,
credentials, build output, and IDE user state are covered by the root
`.gitignore`.
