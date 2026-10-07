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

5. [Async request mechanics](language/kotlin-suspending-ports-and-cancellation.md), then
   [HTTP](modules/project/core/http/README.md) and [health services](modules/project/core/services/README.md).
6. [Native request and screen lifetime](substrate/okhttp-and-compose-effect-lifetime.md), then revisit
   the catalog walkthrough to trace its injected scenarios.

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
The HTTP slice on 2026-10-08 adds request admission, native transport,
problem/JSON decoding, deadlines, health services and six interactive catalog
scenarios. Both platforms consume the same response/path/retry fixtures.
Twelve HTTP and two service host tests passed; the API 36 APK and existing app unit tests passed. Three instrumented tests passed on API36_Test (Android 16): the existing
starter greeting check and two health checks covering all six scenarios and
replacement of an in-flight request. The AndroidX runner is explicit in the
app build.
Diagnostic observers receive original dependency errors; durable reporting
infrastructure remains separate. Session, persistence, sync and graphics are
future capabilities.

## Questions for the next session

- Why can valid JSON still be invalid domain data?
- How does a request deadline differ from a screen canceling its work?
- Which layer should renew credentials and decide retries?
- Which loading, failure and retry controls are ready for the first reusable UI slice?
