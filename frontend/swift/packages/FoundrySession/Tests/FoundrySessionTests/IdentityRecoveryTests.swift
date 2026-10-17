import Foundation
import FoundryHTTP
import FoundryKernel
@testable import FoundrySession
import Testing

@MainActor @Test func recoveryRoutesSeparatePublicAndLeaseClientsAndRefuseWrongPurpose() async throws {
    let publicClient = RecoveryClient(), auth = RecoveryClient()
    let service = HTTPIdentityRecovery(publicClient: publicClient, authenticatedClient: auth)
    let email = "person@example.com", secret = "a private passphrase"
    let verify = "e1." + String(repeating: "A", count: 43), reset = "p1." + String(repeating: "A", count: 43)
    try await service.requestVerification(email: email, password: secret).get()
    try await service.verify(token: verify).get()
    try await service.requestReset(email: email).get()
    try await service.reset(token: reset, password: secret).get()
    try await service.change(currentPassword: secret, newPassword: "another private password").get()
    #expect(await auth.paths == ["identity/email/request", "identity/password/change"])
    #expect(await publicClient.paths == ["identity/email/verify", "identity/password/request-reset", "identity/password/reset"])
    if case .success = try await service.reset(token: verify, password: secret) { Issue.record("Wrong-purpose token sent") }
    if case .success = try await service.verify(token: reset) { Issue.record("Wrong-purpose token sent") }
    if case .success = try await service.change(currentPassword: secret, newPassword: "short") { Issue.record("Short password sent") }
    #expect(await publicClient.paths.count == 3); #expect(await auth.paths.count == 2)
    let anonymous = HTTPIdentityRecovery(publicClient: publicClient)
    if case .success = try await anonymous.requestVerification(email: email, password: secret) { Issue.record("Missing authenticated seam admitted") }
    #expect(String(describing: RecoveryContact(email: email, verifiedAt: nil)) == "RecoveryContact(<redacted>)")
    #expect(!String(reflecting: RecoveryContact(email: email, verifiedAt: nil)).contains(email))
}

@MainActor @Test func contactProjectionRejectsWrongTypesAndUnexpectedFields() async throws {
    for body in [JSONValue.object(["verified": .string("true")]), .object(["verified": .bool(false), "email": .string("private@example.com")])] {
        let client = RecoveryClient(contactBody: body)
        if case .success = try await HTTPIdentityRecovery(publicClient: client, authenticatedClient: client).contact() { Issue.record("Malformed contact admitted") }
    }
    let client = RecoveryClient(contactBody: .object(["verified": .bool(false)]))
    #expect(try await HTTPIdentityRecovery(publicClient: client, authenticatedClient: client).contact().get().email == nil)
}

private actor RecoveryClient: HTTPClient {
    var paths: [String] = []
    let contactBody: JSONValue
    init(contactBody: JSONValue = .null) { self.contactBody = contactBody }
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        paths.append(path)
        return .success(.init(body: contactBody, metadata: .init(status: method == .GET ? 200 : path.hasSuffix("request-reset") ? 202 : 204)))
    }
}
