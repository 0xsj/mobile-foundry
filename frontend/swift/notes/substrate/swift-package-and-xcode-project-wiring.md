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

## Simulator and device signing investigation

Claim: a development-team diagnostic must be checked against the current run
destination and latest build; simulator execution does not establish device
signing readiness.

Observed 2026-10-08 with Xcode 26.2. The app's generated project had no
`DEVELOPMENT_TEAM`, and Xcode displayed an earlier signing failure while an
iPhone 17 Pro simulator was selected. A Debug simulator `xcodebuild` completed
successfully without a `CODE_SIGNING_ALLOWED=NO` override. Clicking Run in Xcode
then built and launched FoundryCatalog on that simulator, and the Issues
navigator had no errors. No signing configuration was changed. This establishes
that the current simulator path works; the cause of the earlier failure was
not reproduced.

For a physical iPhone, use the app target's Signing & Capabilities panel to
enable automatic signing and select the intended team. Persist the chosen
`DEVELOPMENT_TEAM` in the app target's `project.yml` settings, because a GUI-only
project edit is lost on regeneration. TestFlight requires an Apple Developer
Program team. Primary references: Apple's
[simulated and physical devices](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices)
and [distribution preparation](https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution).

The simulator check neither creates a device provisioning profile nor verifies
an archive, device launch or TestFlight upload. Next question: which team and
registered bundle identifier should own the eventual distribution build?

## Generated orientation metadata

Claim: an app targeting both iPhone and iPad must declare its supported
orientations in the built bundle; simulator build success alone does not catch
missing distribution metadata.

Observed 2026-10-08 with Xcode 26.2 and XcodeGen 2.46.0. App Store Connect
validation rejected the user's archive because no orientations were specified
for its iPad multitasking support. The target already selected device families
1 and 2, but its generated Info.plist had no orientation build settings.
Apple defines the property and device-specific variants in
[UISupportedInterfaceOrientations](https://developer.apple.com/documentation/bundleresources/information-property-list/uisupportedinterfaceorientations).

The generic `INFOPLIST_KEY_UISupportedInterfaceOrientations` build setting now
declares portrait and both landscape orientations. Its `_iPad` counterpart
adds portrait upside down. Xcode emits the latter as the device-specific
`UISupportedInterfaceOrientations~ipad` array; the build-setting suffix and
final plist key use different spelling. This keeps iPad support and avoids
opting out of multitasking to bypass the missing metadata.

Regeneration also preserves the development team the user subsequently selected
in Xcode by recording it in the generator's source settings. That is the working
catalog's team, not a team another consumer should inherit when making a new app.

Example: after editing the generator specification, run `make ios-generate`
and `make ios-build`, then inspect the packaged app's Info.plist with `plutil`.
Check the arrays and `UIDeviceFamily` rather than relying only on a source diff.
An archive is a separate snapshot: Product → Archive must create a new one after
the fix. Retrying distribution of the old archive retains the invalid plist.

Observed verification: project generation and the Debug simulator build pass;
the simulator bundle contains the three generic and four iPad orientations,
and device families `[1, 2]`. Release archive verification is recorded in the
catalog walkthrough. Metadata checks do not establish every screen's rotated
layout, real-camera orientation or successful server-side upload validation.

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

## Clean product identity

Claim: generated target wiring and installed product identity can be separate,
versioned inputs without renaming shared packages or source modules.

Added 2026-10-10 with Xcode 26.2 and XcodeGen 2.46.0. ProductApp's generator
specification maps Debug/Release to Config/Product.xcconfig. That file supplies
bundle/display identity, versions and a blank signing team. The generated
CFBundleDisplayName references PRODUCT_DISPLAY_NAME; runtime configuration reads
the built metadata. Tests override their bundle identity suffix and product name
at target level while inheriting the product configuration.

Apple documents [configuration files](https://developer.apple.com/documentation/xcode/adding-a-build-configuration-file-to-your-project)
and [setting precedence](https://help.apple.com/xcode/mac/current/en.lproj/deva3af9b6ba.html).
Target Build Settings override xcconfig values; do not duplicate the editable
app identity in target settings and then assume the config file wins. The source
module/scheme remains ProductApp. Changing PRODUCT_NAME also changes module and
test host assumptions, so display name and installed bundle identity are the
normal branding edits. An alternative is identity directly in project.yml;
the xcconfig makes the later CLI's product edits smaller and keeps signing input
separate from reusable build wiring.

Example: edit PRODUCT_DISPLAY_NAME and PRODUCT_APP_IDENTIFIER, regenerate using
`make product-ios-generate`, then build using `make product-ios-build`. This slice
tests the default configured metadata; independently renamed output is pending.
Two hosted checks observe actual UITabBar selection, UISwitch events, retained
state and built bundle metadata. Keep product state in the window's entry view
above the scoped theme; the root binding preserves it during style changes.
Window-session state is not UserDefaults persistence or process restoration.

Read [ProductApp](../modules/apps/ProductApp/README.md) and
[extraction closure](../../../../notes/patterns/product-entry-and-extraction-closure.md).

## Independent source copies

Observed 2026-10-10, Xcode 26.2 / Swift 6.2.3 / XcodeGen 2.46.0. Generated output
keeps sibling package and fixture depth, then regenerates Xcode wiring locally.
An Orbit Lab app with com.example.orbitlab builds without source-checkout links
and passes its included Kernel/Query/UI checks. Its built plist contains the new
display name and bundle ID, while module/scheme remains ProductApp.

The first feature imported QueryState/MutationState and Kernel results. Those
modules were available transitively, but their symbols were not linked into the
app: compilation succeeded and linking failed. Declare the local package entries
and link their products explicitly in application/test dependencies, then run
the generated ios-test target. Three hosted cases pass after that correction.
This does not require adding domain providers to the empty profile.

Actual source/checks are in the [acceptance probe](../../../../cli/acceptance/README.md);
read [ProductApp's output record](../modules/apps/ProductApp/README.md#independent-cli-output).
