import FoundryKernel
@testable import FoundryServices
import Testing

@MainActor private final class PushProbe: PushTokenPort {
    let tokens: PushTokenStore
    var calls = 0
    var held: CheckedContinuation<AppResult<Void>, any Error>?
    init(_ tokens: PushTokenStore) { self.tokens = tokens }
    func collect() async throws -> AppResult<Void> {
        calls += 1
        return try await withCheckedThrowingContinuation { held = $0 }
    }
}
@MainActor private func settlePush(_ owner: PushTokenController) async {
    for _ in 0..<1000 { if !owner.busy { return }; await Task.yield() }
    Issue.record("Push token owner did not settle")
}
@MainActor private func waitForPush(_ port: PushProbe) async {
    for _ in 0..<1000 { if port.held != nil { return }; await Task.yield() }
    Issue.record("Push token port was not called")
}
@MainActor private func pushTarget(_ token: String) -> DeliveryTarget {
    DeliveryTarget(route: PushRoute(provider: .fcm, application: "dev.example.app", environment: .production)!, token: token)!
}

@Test @MainActor func pushTokensCollectExplicitlyAndRotationIsOnlyALocalCandidate() async {
    let tokens = PushTokenStore(), port: PushProbe
    port = PushProbe(tokens)
    let owner = PushTokenController(tokens: tokens, port: port, onUnexpectedError: { _ in Issue.record("Unexpected defect") })
    #expect(port.calls == 0 && owner.target == nil)
    owner.collect(); owner.collect(); await waitForPush(port)
    #expect(port.calls == 1 && owner.busy)
    tokens.receive(pushTarget("opaque.first"))
    port.held?.resume(returning: .success(())); port.held = nil; await settlePush(owner)
    #expect(owner.target?.token == "opaque.first" && tokens.revision == 1)
    tokens.receive(pushTarget("opaque.first")); #expect(tokens.revision == 1)
    tokens.receive(pushTarget("opaque.rotated"))
    #expect(owner.target?.token == "opaque.rotated" && tokens.revision == 2 && port.calls == 1)
    #expect(String(describing: owner.target!).contains("<redacted>"))
    owner.collect(); await waitForPush(port)
    #expect(owner.target == nil && tokens.target == nil)
    port.held?.resume(returning: .failure(PushTokenFailures.unavailable)); port.held = nil; await settlePush(owner)
    #expect(owner.failure == PushTokenFailures.unavailable && port.calls == 2 && owner.target == nil)
}

@Test @MainActor func pushTokensClosedScreenDropsLateRequestStateButProviderStoreCanRotate() async {
    let tokens = PushTokenStore(), port: PushProbe
    port = PushProbe(tokens)
    let owner = PushTokenController(tokens: tokens, port: port, onUnexpectedError: { _ in Issue.record("Unexpected defect") })
    owner.collect(); await waitForPush(port); owner.close()
    tokens.receive(pushTarget("opaque.late"))
    port.held?.resume(returning: .failure(PushTokenFailures.unavailable)); port.held = nil
    await Task.yield()
    #expect(owner.target == nil && !owner.busy && owner.failure == nil)
    #expect(tokens.target?.token == "opaque.late")
    owner.collect(); #expect(port.calls == 1)
}

private final class PushDefect: Error {}
@Test @MainActor func pushTokensReportDefectsWithoutPublishingProviderDetails() async {
    let tokens = PushTokenStore(), port: PushProbe
    port = PushProbe(tokens)
    let defect = PushDefect()
    var reported: (any Error)?
    let owner = PushTokenController(tokens: tokens, port: port, onUnexpectedError: { reported = $0 })
    owner.collect(); await waitForPush(port)
    port.held?.resume(throwing: defect); port.held = nil; await settlePush(owner)
    #expect(reported as? PushDefect === defect)
    #expect(owner.failure?.meta.message == "An unexpected error occurred." && owner.target == nil)
}
