# Swift catalog walkthrough

The catalog composes an injected health service and displays loading, admitted domain data, and public failures.

## Origin

Shell initialized 2026-10-07; HTTP example implemented and checked 2026-10-08
with Xcode 26.2, Swift 6.2.3, and an iPhone 17 Pro simulator on iOS 26.2.
This note mirrors `apps/FoundryCatalog/`. [HTTP and health contract](../../../../../../contracts/behavior/http.md) owns health behavior.

## Reading order

1. [project.yml](../../../../apps/FoundryCatalog/project.yml) registers the three local packages and application target.
2. [App entry](../../../../apps/FoundryCatalog/Sources/FoundryCatalogApp.swift) supplies CatalogView through the @main app's WindowGroup.
3. [CatalogView](../../../../apps/FoundryCatalog/Sources/CatalogView.swift) links the HTTP health destination from the Foundation section.
4. [HealthCatalogView](../../../../apps/FoundryCatalog/Sources/HealthCatalogView.swift) owns selected scenario, request generation, phase and composition.
5. [Services walkthrough](../../packages/FoundryServices/README.md) and [HTTP walkthrough](../../packages/FoundryHTTP/README.md) explain the reusable operations below the view.

## Walkthrough

CatalogView puts a List inside NavigationStack. NavigationLink supplies a
HealthCatalogView destination. Other foundation and graphics rows reserve
future work; they do not implement those capabilities.

The health view stores selection, a replay counter, and a phase with `@State`.
Its body renders loading, success(Health), or failure(Failure). Picker chooses
one of six injected responses. Run again increments the replay counter.
[Async ports](../../../language/swift-async-ports-and-continuations.md) explains the Swift types, and [View task lifetime](../../../substrate/urlsession-and-view-task-lifetime.md) explains `.task(id:)`.

Each view task sets Loading, creates the client with the selected injected
transport, and calls HealthService.ready. The transport waits briefly so loading
can be visible. The result is checked for cancellation before state publication.
Timeout uses a slow response and short budget. Replacement/removal cancellation
is silent; an unexpected exception reaches OSLog and deliberate generic copy.

Failure rendering calls publicInfo. Malformed JSON therefore shows An unexpected
error occurred without the internal message/code, while retaining request ID.
Validation displays admitted field strings; rate limiting displays timing without
automatically retrying. The View uses standard SwiftUI controls; no design system
or global reusable async screen wrapper has been introduced yet.

## Verification and limits

`make ios-generate` and `make ios-build` passed. Package HTTP/services tests
passed. The app was launched on a dedicated iPhone 17 Pro simulator: navigation,
Healthy with status/IDs/version, Malformed with redacted message, and Timeout
with deadline copy were observed through accessibility state and screenshots.
These are injected service examples. They do not establish backend connectivity,
physical-device behavior, VoiceOver interaction, large text, GPU work or all
lifecycle races. Shared and native adapter tests cover request cancellation below
this view; [Verification techniques](../../../../../../notes/techniques/shared-fixtures-and-native-adapters.md) explains those evidence levels.

## Questions for the next session

- Why does the task ID contain a replay counter as well as scenario?
- Which data comes from the service, and which comes from presentation policy?
- Which UI patterns are now concrete enough to extract for another screen?

## Related

[View protocols](../../../language/swift-protocols-and-opaque-return-types.md), [Swift reading order](../../../README.md), and [Setup](../../../../../../docs/SETUP.md).
