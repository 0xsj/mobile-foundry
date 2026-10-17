import Foundation
import FoundryKernel
import FoundryServices
@testable import FoundryCatalog
import Testing

@Suite @MainActor struct PushTokenTests {
    private let route = ApplePushTokens.route(application: "dev.example.app", environment: "sandbox")!

    @Test func missingConfigurationDoesNotCallAPNsOrGuessAnEnvironment() async throws {
        let tokens = PushTokenStore(); var calls = 0
        let provider = ApplePushTokens(tokens: tokens, route: { nil }, register: { calls += 1 })
        #expect(try await provider.collect().map { true } == .failure(PushTokenFailures.unconfigured))
        #expect(calls == 0 && tokens.target == nil)
        for value in [nil, "", "development", "Sandbox", "production "] as [String?] {
            #expect(ApplePushTokens.route(application: "dev.example.app", environment: value) == nil)
        }
        #expect(ApplePushTokens.route(application: "dev.example.app", environment: "production")?.environment == .production)
    }

    @Test func delegateBytesPublishHexAndSubsequentRotationNeverStartsRegistration() async throws {
        let tokens = PushTokenStore(); var calls = 0
        let provider = ApplePushTokens(tokens: tokens, route: { route }, register: { calls += 1 })
        provider.didRegister(Data([1])); #expect(tokens.target == nil)
        let pending = Task { try await provider.collect() }
        while calls == 0 { await Task.yield() }
        provider.didRegister(Data([0, 15, 128, 255]))
        #expect(try await pending.value.map { true } == .success(true))
        #expect(tokens.target?.token == "000f80ff" && tokens.target?.route == route)
        provider.didRegister(Data(repeating: 1, count: 64))
        #expect(tokens.target?.token.count == 128 && calls == 1)
        provider.didFail(); #expect(tokens.target == nil)
    }

    @Test func cancellationAndTimeoutReleaseWaitersWithoutDoubleResume() async throws {
        let tokens = PushTokenStore(); var calls = 0
        let provider = ApplePushTokens(tokens: tokens, route: { route }, register: { calls += 1 }, timeout: .milliseconds(30))
        let pending = Task { try await provider.collect() }
        while calls == 0 { await Task.yield() }
        pending.cancel()
        do { _ = try await pending.value; Issue.record("Expected cancellation") } catch is CancellationError {}
        let result = try await provider.collect()
        #expect(result.map { true } == .failure(PushTokenFailures.unavailable) && calls == 2)
        // A late process callback is a current address, never a session acknowledgement.
        provider.didRegister(Data([15])); #expect(tokens.target?.token == "0f")
        provider.didRegister(Data()); #expect(tokens.target == nil)
    }
}
