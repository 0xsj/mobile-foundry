# Swift physics module

FoundryPhysics owns bounded 2D toss simulation values, while the app owns screen
input, timing and drawing.

## Origin and reading order

Implemented 2026-10-11 using Swift 6.2.3. Read the
[contract](../../../../../../contracts/behavior/physics-playground.md), then
[ThrowSimulation](../../../../packages/FoundryPhysics/Sources/FoundryPhysics/ThrowSimulation.swift),
[numeric checks](../../../../packages/FoundryPhysics/Tests/FoundryPhysicsTests/ThrowSimulationTests.swift),
and [catalog consumer](../../../../apps/FoundryCatalog/Sources/Physics/PhysicsPlaygroundView.swift).
The [shared pattern](../../../../../../notes/patterns/fixed-step-physics-and-presentation.md)
explains solver time and rendering separation.

## Walkthrough and mechanics

`ThrowSimulation` is an Equatable, Sendable struct. `private(set)` exposes readable
state while requiring launch/reset/configure/advance to mutate it. A local preview
copies the struct and runs the same integration/contact rules without changing
live state. A SwiftUI `@State` owns the live value and publishes mutating updates.

The screen flips Y once at its Canvas/input boundary. CGFloat gesture sizes are
converted to Double explicitly before constructing world vectors. Native sliders
produce a velocity from angle/power and call exactly the same launch boundary as
the gesture. Court, scoreboard and control builders are separate expressions so
the compiler does not infer one enormous generic view tree.

`.task(id: running)` cancels its previous task whenever presence, scene phase,
manual pause or flight status changes. ContinuousClock is monotonic; a fresh
baseline on task restart excludes paused wall time. `Task.sleep` schedules an
approximate 16 ms wakeup, while the solver chooses the fixed numerical time step.
All screen state updates stay on the native UI owner.

Swift Testing's `#expect` macro rewrites calls for diagnostics; a mutating struct
method cannot be evaluated on that macro's immutable argument. Tests first store
`let launched = world.launch(...)`, then assert the Bool. The original attempted
inline call failed compilation; this is a test-expression issue, not a reason to
make the simulation a shared mutable reference.

## Verification and limits

The pure suite's five tests pass, including shared swish/rim/zone cases and
50,000+ finite/world-bound step checks. The iOS catalog builds after extracting
the view expressions and converting gesture values. UI interaction evidence is
recorded in the app walkthrough after the focused native run. No general rigid-body
engine, spin, 3D renderer, durable session or independently generated CLI physics
profile is claimed.

## References and next questions

- [Canvas API](https://developer.apple.com/documentation/swiftui/canvas).
- [View task lifetime](../../../substrate/urlsession-and-view-task-lifetime.md).
- When would contact tunneling justify swept collision tests?
- How would an interpolated render snapshot differ from changing the solver step?
