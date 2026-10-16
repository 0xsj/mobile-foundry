# Physics playground construction

Implemented slice, 2026-10-11. [Behavior](../../contracts/behavior/physics-playground.md)
owns the guarantees. The catalog route is Studio → Open catalog → Physics
playground on both platforms.

## Placement and flow

Swift [FoundryPhysics](../../frontend/swift/packages/FoundryPhysics/Package.swift)
and Kotlin [core/physics](../../frontend/kotlin/project/core/physics/build.gradle.kts)
are pure numeric modules with no UI, Graphics, storage or service dependency.
They expose admitted settings, vectors and a ThrowSimulation. Swift uses mutating
value semantics; Kotlin returns new immutable snapshots with private copy visibility.

App [Swift presentation](../../frontend/swift/apps/FoundryCatalog/Sources/Physics/PhysicsPlaygroundView.swift)
and [Kotlin presentation](../../frontend/kotlin/project/app/src/main/java/dev/mobilefoundry/catalog/ui/physics/PhysicsPlaygroundScreen.kt)
map the same world to native Canvas drawing. A drag from the ball or an explicit
angle/power launch changes the simulation; the feature scheduler advances it and
projects current position/score. Preview runs a separate copy. Shared UI supplies
tokens and control containers. CPU integration and native drawing have separate
ownership; this does not introduce a GPU compute physics engine.

Swift's cancellable `.task(id: running)` uses ContinuousClock and a 16 ms wakeup.
Compose uses `LaunchedEffect(running)` and `withFrameNanos`. Both supply elapsed
time to a fixed 120 Hz accumulator and exclude inactive/manual-pause time. Native
frame scheduling is approximate; tests check solver cadence, not device FPS.

## Checks

Run `make physics-test` for both pure suites, `make ios-build` and
`make android-build` for catalog packaging, and `make physics-ui-test` for focused
Android interactions. The Xcode scheme includes FoundryCatalogUITests; a focused
run also has the `make physics-ios-ui-test` target. An explicit focused
run uses `-only-testing:FoundryCatalogUITests/PhysicsPlaygroundUITests` with
`xcodebuild test`. [Swift checks](../../frontend/swift/packages/FoundryPhysics/Tests/FoundryPhysicsTests/ThrowSimulationTests.swift)
and [Kotlin checks](../../frontend/kotlin/project/core/physics/src/test/kotlin/dev/mobilefoundry/physics/ThrowSimulationTest.kt)
consume the same shot fixture, verify numerical free flight, 30/60/120 Hz
equivalence, floor/wall/board impulses, invalid inputs, stall caps, preview purity,
reset, and bounded launches in multiple directions. Native controls/gestures have
their own [Swift](../../frontend/swift/apps/FoundryCatalog/UITests/PhysicsPlaygroundUITests.swift)
and [Android](../../frontend/kotlin/project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/physics/PhysicsPlaygroundTest.kt)
tests.

The initial guessed shot (5.5, 8.1) clips the left rim under the actual collision
geometry. It is retained as a miss fixture; (5.0, 8.6) clears the rim. A shot
that hits the backboard may legitimately bank into the hoop, so its contact test
asserts velocity reversal and bounded scoring rather than requiring a miss.
The first Swift UI expression exceeded compiler type-checking limits; extracting
court/controls/gesture builders exposed ambiguous CGFloat/Double multiplication,
which is now converted explicitly at the world-coordinate boundary.

Observed acceptance, 2026-10-11: five numeric tests pass per platform; both catalog
apps build; two iPhone 17 Pro / iOS 26.2 interactions and three Pixel 9 Pro /
Android 15 interactions pass with zero failures/skips. The iOS retained screenshot and a post-test Android capture
were inspected for actual court/control layout, bounded preview and visible actions. Android navigation/ScrollBy must
finish before tests freeze display time; controlling the frame clock during
native scroll animation stalled the first control probe. A fresh read-only
emulator and separate ADB server avoided a shared device-response stall.

This is a catalog capability with separately reusable numeric modules. Existing
Daily/Workspace/Browse CLI inventories remain unchanged. Generic worlds, spin,
multiple bodies, native 3D rendering, durable scores and CLI extraction remain
future slices. See [starter readiness](../STARTER-READINESS.md).
