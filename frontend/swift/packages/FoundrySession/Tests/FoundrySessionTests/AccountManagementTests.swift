import Foundation
import FoundryHTTP
import FoundryKernel
@testable import FoundrySession
import Testing

@Test func workspaceSharedNameAdmissionAndScalarLimits() throws {
    struct Row: Decodable { let input: String; let valid: Bool; let canonical: String? }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appending(path: "contracts/fixtures/account/names.json")))
    for row in rows {
        let answer = AccountName.make(row.input)
        if row.valid { #expect(try answer.get().value.utf8.elementsEqual(row.canonical!.utf8)); #expect(GoSessionWire.name(row.canonical!)) }
        else if case .success = answer { Issue.record("Invalid name admitted") }
    }
    #expect(GoSessionWire.name("\u{200B}Project\u{200B}"))
    #expect(!GoSessionWire.name(" Project "))
    #expect(try AccountName.make("\u{0085} 🧱 \u{0085}").get().value == "🧱")
    #expect(try AccountName.make(String(repeating: "🧱", count: 80)).get().value.unicodeScalars.count == 80)
    if case .success = AccountName.make(String(repeating: "🧱", count: 81)) { Issue.record("81 scalars admitted") }
    #expect(String(reflecting: try AccountName.make("Private project").get()) == "AccountName(<redacted>)")
}
@MainActor @Test func workspaceHTTPUsesOneAttemptStrongVersionAndNoReplayKey() async throws {
    let name = try AccountName.make(" Cafe\u{301} ").get(), client = WorkspaceClient()
    let service = HTTPAccountManagement(client: client, principalID: workspaceOwner)
    let before = try await service.read(workspaceID).get()
    await client.reply(workspaceBody(name: name.value, version: 2), status: 200, etag: "\"2\"")
    let renamed = try await service.rename(before, to: name).get()
    #expect(renamed.version == 2)
    let options = try #require(await client.options)
    #expect(options.headers == ["If-Match": "\"1\""] && options.idempotencyKey == nil)
    #expect(await client.method == .PATCH); #expect(await client.path == "accounts/\(workspaceID)"); #expect(await client.calls == 2)
    await client.reply(workspaceBody(name: name.value), status: 201, etag: "\"1\"")
    _ = try await service.create(name).get()
    #expect(await client.options?.headers.isEmpty == true); #expect(await client.path == "accounts")
    await client.fail(.timeout(.init(message: "Lost reply")))
    if case .failure(let failure) = try await service.create(name) { #expect(failure.kind == .timeout) } else { Issue.record("Lost write succeeded") }
    #expect(await client.calls == 4)
}
@MainActor @Test func workspaceHTTPRejectsForeignOwnerETagVersionTargetAndNormalizationChanges() async throws {
    let name = try AccountName.make("Cafe\u{301}").get()
    let before = ManagedAccount(id: workspaceID, ownerID: workspaceOwner, name: "Personal", version: 1, createdAtMicros: 1_767_225_600_000_000, updatedAtMicros: 1_767_225_600_000_000)!
    for (body, etag) in [(workspaceBody(name: name.value, version: 2), "W/\"2\""), (workspaceBody(name: name.value, version: 3), "\"3\""),
        (workspaceBody(name: "Café", version: 2), "\"2\""), (workspaceBody(name: name.value, version: 2, id: workspaceOwner), "\"2\""),
        (workspaceBody(name: name.value, version: 2, owner: workspaceID), "\"2\"")] {
        let client = WorkspaceClient(); await client.reply(body, status: 200, etag: etag)
        if case .success = try await HTTPAccountManagement(client: client, principalID: workspaceOwner).rename(before, to: name) { Issue.record("Unrelated rename result admitted") }
        #expect(await client.calls == 1)
    }
    let client = WorkspaceClient()
    let task = Task { try await HTTPAccountManagement(client: client, principalID: workspaceOwner).create(name) }; task.cancel()
    do { _ = try await task.value; Issue.record("Canceled write dispatched") } catch is CancellationError {}
    #expect(await client.calls == 0)
}
private let workspaceID = "00000000-0000-4000-8000-000000000001"
private let workspaceOwner = "00000000-0000-4000-8000-000000000002"
private func workspaceBody(name: String = "Personal", version: Double = 1, id: String = workspaceID, owner: String = workspaceOwner) -> JSONValue {
    .object(["id": .string(id), "owner_id": .string(owner), "name": .string(name), "version": .number(version), "created_at": .string("2026-01-01T00:00:00Z"), "updated_at": .string("2026-01-01T00:00:00Z")])
}
private actor WorkspaceClient: HTTPClient {
    var body = workspaceBody(); var status = 200; var etag = "\"1\""; var failure: Failure?
    var calls = 0; var options: RequestOptions?; var method: HTTPMethod?; var path: String?
    func reply(_ body: JSONValue, status: Int, etag: String) { self.body = body; self.status = status; self.etag = etag; failure = nil }
    func fail(_ failure: Failure) { self.failure = failure }
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        calls += 1; self.options = options; self.method = method; self.path = path
        return failure.map { .failure($0) } ?? .success(.init(body: body, metadata: .init(status: status, etag: etag)))
    }
}
