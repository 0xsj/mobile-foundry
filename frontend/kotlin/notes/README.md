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
7. [Service interfaces and StateFlow](language/kotlin-service-interfaces-and-stateflow.md),
   then the services/catalog walkthroughs' notes sections and [provider seams](../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state).
8. [Covariant state and content slots](language/kotlin-covariant-query-state-and-content-slots.md),
   then [core/query](modules/project/core/query/README.md),
   [core/ui](modules/project/core/ui/README.md), and
   [query/rendering ownership](../../../notes/patterns/query-state-and-rendering.md).

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

The 2026-10-08 editor investigation in [Gradle bootstrap](substrate/gradle-and-android-bootstrap.md#editor-dependency-imports)
explains why valid build dependencies appeared unresolved in VSCodium. A task-specific
configuration-cache workaround restored the health service's editor classpath;
the editor showed zero problems after reloading, and normal build checks still
stored their configuration cache.

The 2026-10-08 notes/query slice adds a provider-independent NotesService port,
memory and HTTP adapters, an injected ViewModel, and one list renderer. Five
service tests (three new), eight notes ViewModel tests and the two starter tests
passed. Both providers run the same feature-state scenarios. All six device
tests passed on API36_Test/Android 16, including three new notes tests for provider
switching, content/empty/failure, retry, cancel and navigation-entry reopening. HTTP is injected; persistence
and real provider SDK integration remain deferred.

The 2026-10-08 query/UI extraction adds a pure Kotlin query module and an
Android Compose UI library. Two query tests passed against nine shared cases;
eight notes ViewModel and two starter regressions still pass. The modules and
debug APK built. All ten device tests passed on API36_Test/Android 16, including
four new presentation-matrix, callback, public-copy and gallery checks.
The scalar gallery and notes list now consume the same reusable state/UI.

## Questions for the next session

- Why can valid JSON still be invalid domain data?
- How does a request deadline differ from a screen canceling its work?
- Which layer should renew credentials and decide retries?
- Which form and mutation behavior should accompany the next reusable controls?
- How can editor dependency imports fail while the same source compiles in Gradle?
- Why does a navigation entry need its own ViewModelStoreOwner?
- Why do pure state transformations leave coroutine admission to the feature?
