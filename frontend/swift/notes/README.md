# Swift learning notes

Read the Swift implementation through its module walkthroughs, then use the
linked language and tool notes to understand the choices. This notebook follows
[the shared workflow](../../../docs/NOTES.md) and Bento's categories.

## Reading order

1. [Swift package and Xcode project wiring](substrate/swift-package-and-xcode-project-wiring.md).
2. [Protocols and opaque return types](language/swift-protocols-and-opaque-return-types.md).
3. [Catalog shell walkthrough](modules/apps/FoundryCatalog/README.md), following
   the source links from app composition to the displayed list.
4. [Result and failure values](language/swift-result-and-failure-values.md), then
   [the kernel walkthrough](modules/packages/FoundryKernel/README.md).

5. [Async request mechanics](language/swift-async-ports-and-continuations.md), then
   [HTTP](modules/packages/FoundryHTTP/README.md) and [health services](modules/packages/FoundryServices/README.md).
6. [Native request and screen lifetime](substrate/urlsession-and-view-task-lifetime.md), then revisit
   the catalog walkthrough to trace its injected scenarios.
7. [Observable stores and service protocols](language/swift-observable-stores-and-service-protocols.md),
   then the services/catalog walkthroughs' notes sections and [provider seams](../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state).
8. [Generic state and content builders](language/swift-generic-query-state-and-content-builders.md),
   then [FoundryQuery](modules/packages/FoundryQuery/README.md),
   [FoundryUI](modules/packages/FoundryUI/README.md), and
   [query/rendering ownership](../../../notes/patterns/query-state-and-rendering.md).

## Findings by lifespan

| Directory | Subject |
| --- | --- |
| `modules/<source-path>/` | Local code flow, decisions, and verification |
| `language/` | Transferable Swift mechanics |
| `patterns/` | Architectural approaches specific to the Swift implementation |
| `concepts/` | Domain distinctions needing a Swift-specific explanation |
| `techniques/` | Swift investigation and verification methods |
| `substrate/` | Versioned Xcode, SwiftUI, SDK, package, and tool behavior |

Paths mirror the Swift root: `apps/FoundryCatalog/` maps to
`notes/modules/apps/FoundryCatalog/`. Module notes link source and checks;
transferable notes describe the mechanism without local source paths.

## Current coverage

The 2026-10-07 bootstrap links a SwiftUI app to an empty Swift kernel library.
The package and iOS simulator builds passed. The catalog shows planned areas;
it has no feature navigation, service calls, or renderer yet.

The 2026-10-08 kernel slice implements typed outcomes, immutable failures, and
public projection. Seven host tests passed against shared fixtures. The
language note includes the verified rejection of a throwing Result.map callback.
Shared reasoning is in [expected failures and diagnostics](../../../notes/concepts/expected-failures-and-diagnostics.md).
The HTTP slice on 2026-10-08 adds request admission, native transport,
problem/JSON decoding, deadlines, health services and six interactive catalog
scenarios. Both platforms consume the same response/path/retry fixtures.
Thirteen HTTP and two service host tests passed; the simulator app built, and Healthy, Malformed, and Timeout presentation were observed on iOS 26.2.
Diagnostic observers receive original dependency errors; durable reporting
infrastructure remains separate. Session, persistence, sync and graphics are
future capabilities.

The 2026-10-08 notes/query slice adds a provider-independent NotesService port,
memory and HTTP adapters, an observable main-actor store, and one list renderer.
Five service package tests (three new) and seven iOS store tests passed. The
simulator accessibility state confirmed memory/HTTP content and HTTP empty/
unavailable presentation. HTTP remains injected; screenshots were blank in this
session, so visual layout and assistive-technology quality remain unverified.

The 2026-10-08 query/UI extraction adds generic value state and native async
presentation. Two query package tests passed against nine shared cases, and
all seven iOS store regressions still pass. The app and UI package compiled
through `make ios-test`. Simulator interactions confirmed initial/retained
states, empty retention, public failure copy, Retry and Cancel. A current
screenshot showed the populated gallery's native layout; comprehensive
accessibility, text-size and appearance checks remain future work.

## Questions for the next session

- Why can valid JSON still be invalid domain data?
- How does a request deadline differ from a screen canceling its work?
- Which layer should renew credentials and decide retries?
- Which form and mutation behavior should accompany the next reusable controls?
- Which query behaviors require a shared cache, and which belong to one feature's store?
- What does pure settlement omit that a feature must check before publishing?
