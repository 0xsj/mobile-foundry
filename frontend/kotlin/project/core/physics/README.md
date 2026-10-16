# Physics

Pure Kotlin 2D toss simulation with admitted settings, immutable snapshots,
fixed steps, contacts and hoop/landing-zone scoring. UI and frame scheduling
belong to the caller.

Run `./gradlew :core:physics:test` from `frontend/kotlin/project`, or
`make physics-test` from the repository root for both native numeric suites.
Read the [contract](../../../../../contracts/behavior/physics-playground.md)
and [walkthrough](../../../notes/modules/project/core/physics/README.md).
