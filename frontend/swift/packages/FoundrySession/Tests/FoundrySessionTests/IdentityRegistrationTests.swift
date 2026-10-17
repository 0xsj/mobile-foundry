import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Testing

@Test func registrationSharedAdmissionPreservesPasswordAndRedactsDescriptions() throws {
    struct Row: Decodable { let name: String; let input: [String: String]; let valid: Bool; let expected: [String: String]? }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appending(path: "contracts/fixtures/native-registration/inputs.json")))
    for row in rows {
        let answer = RegisterIdentity.make(login: row.input["login"]!, displayName: row.input["display_name"]!, password: row.input["password"]!)
        if row.valid {
            let command = try answer.get()
            #expect(command.login == row.expected?["login"] && command.displayName.utf8.elementsEqual(row.expected!["display_name"]!.utf8))
            #expect(command.password.utf8.elementsEqual(row.input["password"]!.utf8))
            #expect(String(reflecting: command) == "RegisterIdentity(<redacted>)")
        } else if case .success = answer { Issue.record("Admitted \(row.name)") }
    }
    let result = RegisteredIdentity(id: "00000000-0000-4000-8000-000000000001", login: "alex", displayName: "Alex")!
    #expect(RegisteredIdentity(id: "memory.member", login: "alex", displayName: "Alex") != nil)
    #expect(String(reflecting: result) == "RegisteredIdentity(<redacted>)")
    #expect(RegisteredIdentity(id: result.id, login: "alex\r", displayName: "Alex") == nil)
}
@MainActor @Test func registrationHTTPMakesOnePublicAttemptWithoutIdempotencyOrSignIn() async throws {
    let command = try RegisterIdentity.make(login: "  ALEX  ", displayName: " Alex ", password: "private passphrase").get()
    let client = RegistrationClient(body: registrationBody())
    let registered = try await HTTPIdentityRegistration(client: client).register(command).get()
    #expect(registered.confirms(command))
    #expect(await client.calls == 1); #expect(await client.method == .POST); #expect(await client.path == "identity/register")
    let options = try #require(await client.options)
    #expect(options.headers.isEmpty && options.idempotencyKey == nil)
    #expect(options.body == .object(["login": .string("alex"), "display_name": .string("Alex"), "password": .string("private passphrase")]))
    let failed = RegistrationClient(failure: .timeout(.init(message: "Lost reply")))
    if case .failure(let failure) = try await HTTPIdentityRegistration(client: failed).register(command) { #expect(failure.kind == .timeout) }
    else { Issue.record("Failed registration admitted") }
    #expect(await failed.calls == 1)
}
@MainActor @Test func registrationHTTPRefusesUnexpectedStatusFieldsOrDifferentInput() async throws {
    let command = try RegisterIdentity.make(login: "alex", displayName: "Cafe\u{301}", password: "private passphrase").get()
    let good = registrationBody(name: command.displayName)
    for (status, body) in [(200, good), (201, registrationBody(name: "Café")), (201, registrationBody(login: "sam", name: command.displayName)),
        (201, .object(["id": .string("bad"), "login": .string("alex"), "display_name": .string(command.displayName)])),
        (201, .object(good.object!.merging(["access_token": .string("not-a-grant")]) { _, new in new }))] {
        let client = RegistrationClient(body: body, status: status)
        if case .failure(let failure) = try await HTTPIdentityRegistration(client: client).register(command) {
            #expect(failure.kind == .internalError && failure.meta.requestID == "owned-request")
        } else { Issue.record("Unexpected registration reply admitted") }
        #expect(await client.calls == 1)
    }
}
@MainActor @Test func canceledRegistrationNeverDispatchesAndConfigurationRefusesRemoteHTTP() async throws {
    let command = try RegisterIdentity.make(login: "alex", displayName: "Alex", password: "private passphrase").get()
    let client = RegistrationClient(body: registrationBody())
    let task = Task { try await HTTPIdentityRegistration(client: client).register(command) }; task.cancel()
    do { _ = try await task.value; Issue.record("Canceled registration completed") } catch is CancellationError {}
    #expect(await client.calls == 0)
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    if case .success = HTTPIdentityRegistration.create(baseURL: "http://example.com/v1", transport: transport, allowLocalHTTP: true) { Issue.record("Remote cleartext admitted") }
}
private func registrationBody(login: String = "alex", name: String = "Alex") -> JSONValue {
    .object(["id": .string("00000000-0000-4000-8000-000000000001"), "login": .string(login), "display_name": .string(name)])
}
private actor RegistrationClient: HTTPClient {
    let body: JSONValue; let status: Int; let failure: Failure?
    var calls = 0; var method: HTTPMethod?; var path: String?; var options: RequestOptions?
    init(body: JSONValue = .null, status: Int = 201, failure: Failure? = nil) { self.body = body; self.status = status; self.failure = failure }
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        calls += 1; self.method = method; self.path = path; self.options = options
        if let failure { return .failure(failure) }
        return .success(.init(body: body, metadata: .init(status: status, requestID: "owned-request")))
    }
}
