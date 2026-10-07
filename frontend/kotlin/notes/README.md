# Kotlin learning notes

Follow the Android implementation through source walkthroughs, with separate
notes for Kotlin mechanics and framework behavior. This notebook follows
[the shared workflow](../../../docs/NOTES.md) and Bento's categories.

## Reading order

1. [Gradle and Android bootstrap](substrate/gradle-and-android-bootstrap.md).
2. [Sealed UI states and data classes](language/kotlin-sealed-ui-states-and-data-classes.md).
3. [Android starter walkthrough](modules/project/app/README.md), from the
   activity through navigation, repository, view model, and screen.
4. [Outcomes and owned payloads](language/kotlin-outcomes-and-owned-payloads.md),
   then [the kernel walkthrough](modules/project/core/kernel/README.md).

## Findings by lifespan

| Directory | Subject |
| --- | --- |
| `modules/<source-path>/` | Local code flow, decisions, and verification |
| `language/` | Transferable Kotlin mechanics |
| `patterns/` | Architectural approaches specific to the Kotlin implementation |
| `concepts/` | Domain distinctions needing a Kotlin-specific explanation |
| `techniques/` | Kotlin investigation and verification methods |
| `substrate/` | Versioned Android, Compose, Gradle, and dependency behavior |

Paths mirror the Kotlin root, including `project/`: `project/app/` maps to
`notes/modules/project/app/`. Reusable explanations stay independent of source
paths; module walkthroughs link their actual use.

## Current coverage

The 2026-10-07 bootstrap builds the generated Compose app and the empty
Kotlin/JVM kernel module. The debug APK and two starter unit tests passed.
The generated app illustrates a repository/view-model/screen flow; its raw
Throwable error state is starter code rather than the selected failure policy.

The 2026-10-08 kernel slice implements typed outcomes, immutable failures, and
public projection. Nine kernel host tests passed against shared fixtures,
including callback exception identity and cancellation-exception propagation.
Shared reasoning is in [expected failures and diagnostics](../../../notes/concepts/expected-failures-and-diagnostics.md).
HTTP, async adapters, UI examples, and diagnostic reporting remain future slices.

## Questions for the next session

- What information belongs in a UI state variant, and what belongs in a
  foundation failure value?
- Which part of the starter owns data production, and which part renders it?
- What would a check need to observe to establish the Success transition?
- Where should the first HTTP adapter classify known dependency failures while
  preserving coroutine cancellation and unexpected defects?
