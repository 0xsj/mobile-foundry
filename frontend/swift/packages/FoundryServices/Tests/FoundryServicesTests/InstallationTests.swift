import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private let installationID = "00000000-0000-4000-8000-000000000001"
private let registrationID = "00000000-0000-4000-8000-000000000002"
private let proof = "i1." + String(repeating: "A", count: 43)
private let installation = InstallationIdentity(id: installationID, key: proof)!
private let route = PushRoute(provider: .apns, application: "dev.example.app", environment: .sandbox)!
private actor InstallationWire: HTTPTransport {
    let response: WireResponse; var requests: [PreparedRequest] = []
    init(_ response: WireResponse) { self.response = response }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> { requests.append(request); return .success(response) }
}
@Test func installationSharedAdmissionAndSecretInput() async throws {
    struct Row: Decodable { let name: String; let body: JSONValue; let etag: String; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/installations/records.json")))
    for row in rows {
        let wire = InstallationWire(.init(status: 200, headers: ["etag": row.etag], body: try JSONEncoder().encode(row.body)))
        let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
        let result = try await HTTPInstallationRegistry(client: client).read(installation)
        if row.valid { #expect(try result.get()?.id == installationID) }
        else if case .success = result { Issue.record("Admitted \(row.name)") }
        let sent = await wire.requests; #expect(sent.count == 1)
        #expect(sent[0].url.path == "/v1/installations/\(installationID)" && sent[0].url.query == nil)
        #expect(sent[0].headers["x-installation-key"] == proof && sent[0].headers["idempotency-key"] == nil)
    }
    #expect(InstallationIdentity(id: installationID, key: "i1." + String(repeating: "A", count: 42) + "B") == nil)
    #expect(InstallationIdentity(id: installationID, key: proof + "\n") == nil)
    #expect(DeliveryTarget(route: route, token: "bad token") == nil)
    #expect(DeliveryTarget(route: route, token: String(repeating: "x", count: 4097)) == nil)
    #expect(PushRoute(provider: .fcm, application: "dev.example.app", environment: .sandbox) == nil)
    #expect(!String(reflecting: installation).contains(proof))
    #expect(!String(reflecting: DeliveryTarget(route: route, token: "opaque-secret")!).contains("opaque-secret"))
    #expect(try InstallationIdentity.decode(installation.encoded()).get() == installation)
}
private actor FixedInstallationVault: InstallationVault {
    let result: AppResult<InstallationIdentity>
    init(_ result: AppResult<InstallationIdentity> = .success(installation)) { self.result = result }
    func loadOrCreate() async throws -> AppResult<InstallationIdentity> { result }
}
private actor RegistryProbe: InstallationRegistry {
    var record: InstallationRecord?
    var targets: [String] = []
    var registrations = 0; var reads = 0
    var pause = false
    var waiting: CheckedContinuation<AppResult<InstallationRecord?>, Never>?
    init(pause: Bool = false) { self.pause = pause }
    func read(_ identity: InstallationIdentity) async throws -> AppResult<InstallationRecord?> {
        reads += 1
        if pause { return await withCheckedContinuation { waiting = $0 } }
        return .success(record)
    }
    func register(_ identity: InstallationIdentity, registrationID: String, target: DeliveryTarget, replacing: InstallationRecord?) async throws -> AppResult<InstallationRecord> {
        registrations += 1
        targets.append(target.token)
        record = InstallationRecord(id: identity.id, registrationID: registrationID, version: (replacing?.version ?? 0) + 1, route: target.route, registered: true, currentBinding: true, createdAt: Date(timeIntervalSince1970: 100), updatedAt: Date(timeIntervalSince1970: 101), expiresAt: Date(timeIntervalSince1970: 200))!
        return .failure(.timeout(.init(message: "Dropped acknowledgement")))
    }
    func unregister(_ identity: InstallationIdentity, current: InstallationRecord) async throws -> AppResult<InstallationRecord> { .failure(InstallationFailures.invalidInput) }
    func release() { waiting?.resume(returning: .success(record)); waiting = nil }
    func seedOldRecord() { record = InstallationRecord(id: installationID, registrationID: registrationID, version: 9, route: route, registered: true, currentBinding: true, createdAt: Date(timeIntervalSince1970: 100), updatedAt: Date(timeIntervalSince1970: 101), expiresAt: Date(timeIntervalSince1970: 200))! }
}
@MainActor private func settle(_ owner: InstallationController) async { for _ in 0..<1000 { if !owner.busy { return }; await Task.yield() }; Issue.record("Owner did not settle") }
@Test @MainActor func installationCollectedTokenHandoffRetainsInputAndRotationNeedsANewIntent() async {
    let tokens = PushTokenStore(), repo = RegistryProbe()
    let owner = InstallationController(vault: FixedInstallationVault(), onUnexpectedError: { _ in Issue.record("Unexpected defect") })
    owner.bind(repo); await settle(owner)
    tokens.receive(DeliveryTarget(route: route, token: "collected.first")!)
    #expect(await repo.registrations == 0)
    owner.register(tokens.target!)
    tokens.receive(DeliveryTarget(route: route, token: "collected.rotated")!)
    await settle(owner)
    #expect(await repo.targets == ["collected.first"] && owner.needsRefresh)
    owner.register(tokens.target!); #expect(await repo.registrations == 1)
    owner.refresh(); await settle(owner)
    #expect(await repo.registrations == 1 && tokens.target?.token == "collected.rotated")
    owner.register(tokens.target!); await settle(owner)
    #expect(await repo.targets == ["collected.first", "collected.rotated"])
    owner.bind(nil)
    #expect(tokens.target?.token == "collected.rotated" && !owner.canRegister)
}
@Test @MainActor func installationUncertaintyNeedsReadbackAndNeverReplays() async throws {
    let repo = RegistryProbe(); let owner = InstallationController(vault: FixedInstallationVault(), onUnexpectedError: { _ in Issue.record("Unexpected registry defect") })
    owner.bind(repo); await settle(owner)
    #expect(owner.canRegister && owner.record == nil)
    owner.register(DeliveryTarget(route: route, token: "first-token")!); await settle(owner)
    #expect(owner.needsRefresh && !owner.canRegister && owner.record == nil)
    owner.register(DeliveryTarget(route: route, token: "new-token")!)
    #expect(await repo.registrations == 1)
    owner.refresh(); await settle(owner)
    #expect(owner.record?.version == 1 && owner.canUnregister && !owner.needsRefresh)
    #expect(await repo.registrations == 1)
    // Renewal/account switch rebinds the read client; it does not register again.
    owner.bind(repo); await settle(owner)
    #expect(owner.installationID == installationID && owner.record?.version == 1)
    #expect(await repo.registrations == 1)
    owner.bind(nil); #expect(!owner.canRegister && owner.record == nil)
    #expect(try await FixedInstallationVault().loadOrCreate().get().key == proof)
}
@Test @MainActor func installationOldLeaseAndStorageFailuresCannotPublish() async throws {
    let old = RegistryProbe(pause: true); let next = RegistryProbe()
    await old.seedOldRecord()
    let owner = InstallationController(vault: FixedInstallationVault(), onUnexpectedError: { _ in Issue.record("Unexpected registry defect") })
    owner.bind(old)
    for _ in 0..<1000 { if await old.waiting != nil { break }; await Task.yield() }
    owner.bind(next); await settle(owner); await old.release(); await Task.yield()
    #expect(owner.record == nil && owner.canRegister && !owner.busy)
    let blocked = InstallationController(vault: FixedInstallationVault(.failure(InstallationFailures.invalidStorage)), onUnexpectedError: { _ in Issue.record("Unexpected vault defect") })
    blocked.bind(next); await settle(blocked)
    #expect(blocked.failure == InstallationFailures.invalidStorage && !blocked.canRegister)
    #expect(await next.reads == 1)
}
private final class InstallationDefect: Error {}
private struct DefectiveInstallationVault: InstallationVault {
    let defect: InstallationDefect
    func loadOrCreate() async throws -> AppResult<InstallationIdentity> { throw defect }
}
@Test @MainActor func installationUnexpectedDefectsReachReporterWithoutBecomingNetworkFailures() async {
    let defect = InstallationDefect(); var reported: (any Error)?
    let owner = InstallationController(vault: DefectiveInstallationVault(defect: defect), onUnexpectedError: { reported = $0 })
    let repo = RegistryProbe(); owner.bind(repo); await settle(owner)
    #expect((reported as? InstallationDefect) === defect)
    #expect(owner.failure?.kind == .internalError && owner.needsRefresh && !owner.canRegister)
    #expect(await repo.reads == 0)
}
