# Physics

Pure 2D toss simulation with bounded gravity, restitution, wall/rim/backboard
contacts, ground friction and hoop/landing-zone scoring. The caller owns rendering
and frame scheduling. No UI or GPU dependency is required.

Run `swift test --package-path frontend/swift/packages/FoundryPhysics` from the
repository root. Read the [contract](../../../../contracts/behavior/physics-playground.md)
and [walkthrough](../../notes/modules/packages/FoundryPhysics/README.md).
