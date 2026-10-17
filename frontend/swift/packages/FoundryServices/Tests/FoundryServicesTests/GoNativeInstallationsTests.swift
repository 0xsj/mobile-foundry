import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices
import Security

private struct InstallationBearer: HTTPClient {
    let raw: any HTTPClient; let token: String
    func requestEntity(_ method: HTTPMethod, path: String, options o: RequestOptions) async throws -> AppResult<Entity> {
        var h = o.headers; h["authorization"] = "Bearer \(token)"
        return try await raw.requestEntity(method, path: path, options: .init(query: o.query, headers: h, body: o.body, ifMatch: o.ifMatch, idempotencyKey: o.idempotencyKey))
    }
}
private actor DropInstallationAck: HTTPClient {
    let inner: any HTTPClient
    init(_ inner: any HTTPClient) { self.inner = inner }
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        let result = try await inner.requestEntity(method, path: path, options: options)
        if method == .POST, case .success = result { return .failure(.timeout(.init(message: "Acknowledgement deliberately dropped."))) }
        return result
    }
}
@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] != nil))
func nativeGoInstallationReadbackHandoffAndRetirement() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let raw = try DefaultHTTPClient.create(baseURL: base, transport: transport).get()
    let login = "installations-swift-" + UUID().uuidString.lowercased(), password = "native integration passphrase"
    _ = try await raw.requestEntity(.POST, path: "identity/register", options: .init(body: .object(["login":.string(login),"display_name":.string("Installations Swift"),"password":.string(password)]))).get()
    func signIn() async throws -> Entity { try await raw.requestEntity(.POST, path: "identity/sign-in", options: .init(body: .object(["login":.string(login),"password":.string(password)]))).get() }
    let grant = try await signIn()
    var nextGrant = try await signIn()
    let a = InstallationBearer(raw: raw, token: try #require(grant.body.object?["access_token"]?.string))
    let b = InstallationBearer(raw: raw, token: try #require(nextGrant.body.object?["access_token"]?.string))
    var randomBytes = [UInt8](repeating: 0, count: 32)
    #expect(SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes) == errSecSuccess)
    let identity = try #require(InstallationIdentity(id: UUID().uuidString.lowercased(), key: "i1." + Data(randomBytes).base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"")))
    let route = try #require(PushRoute(provider: .apns, application: "dev.example.acceptance", environment: .sandbox))
    let target = try #require(DeliveryTarget(route: route, token: "native-installation-probe-" + identity.id))
    let repo = HTTPInstallationRegistry(client: a)
    var other = HTTPInstallationRegistry(client: b)
    #expect(try await repo.read(identity).get() == nil)
    let checkpoint = UUID().uuidString.lowercased()
    if case .success = try await HTTPInstallationRegistry(client: DropInstallationAck(a)).register(identity, registrationID: checkpoint, target: target, replacing: nil) { Issue.record("Ack was not dropped") }
    let created = try #require(try await repo.read(identity).get())
    #expect(created.registrationID == checkpoint && created.version == 1 && created.currentBinding)
    let foreign = try #require(try await other.read(identity).get()); #expect(!foreign.currentBinding)
    let rebound = try await other.register(identity, registrationID: UUID().uuidString.lowercased(), target: target, replacing: foreign).get()
    #expect(rebound.version == 2 && rebound.currentBinding)
    nextGrant = try await raw.requestEntity(.POST, path: "identity/renew", options: .init(body: .object(["refresh_token": nextGrant.body.object!["refresh_token"]!]))).get()
    other = HTTPInstallationRegistry(client: InstallationBearer(raw: raw, token: try #require(nextGrant.body.object?["access_token"]?.string)))
    #expect(try await other.read(identity).get() == rebound)
    if case .failure(let error) = try await repo.unregister(identity, current: created) { #expect(error.kind == .forbidden) } else { Issue.record("Old session removed newer binding") }
    let oldRefresh = try #require(grant.body.object?["refresh_token"]?.string)
    _ = try await raw.requestEntity(.POST, path: "identity/sign-out", options: .init(body: .object(["refresh_token":.string(oldRefresh)]))).get()
    // An older revoke cannot retire the newer session binding.
    #expect(try await other.read(identity).get()?.registered == true)
    let disabled = try await other.unregister(identity, current: rebound).get()
    #expect(!disabled.registered && disabled.version == 3)
    #expect(try await other.unregister(identity, current: disabled).get() == disabled)
    let rotated = try #require(DeliveryTarget(route: route, token: target.token + ".rotated"))
    let final = try await other.register(identity, registrationID: UUID().uuidString.lowercased(), target: rotated, replacing: disabled).get()
    #expect(final.version == 4)
    _ = try await raw.requestEntity(.POST, path: "identity/sign-out", options: .init(body: .object(["refresh_token": nextGrant.body.object!["refresh_token"]!]))).get()
    let observerGrant = try await signIn()
    let observer = HTTPInstallationRegistry(client: InstallationBearer(raw: raw, token: try #require(observerGrant.body.object?["access_token"]?.string)))
    var retired = false
    for _ in 0..<100 {
        if let value = try await observer.read(identity).get(), !value.registered { #expect(value.version == 5 && !value.currentBinding); retired = true; break }
        try await Task.sleep(for: .milliseconds(30))
    }
    #expect(retired)
    _ = try await raw.requestEntity(.POST, path: "identity/sign-out", options: .init(body: .object(["refresh_token": observerGrant.body.object!["refresh_token"]!]))).get()
}
