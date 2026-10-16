import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private struct BearerNotesHTTP: HTTPClient {
    let client: any HTTPClient; let token: String
    func requestEntity(_ method: HTTPMethod,path: String,options: RequestOptions) async throws -> AppResult<Entity> {
        try await client.requestEntity(method,path: path,options: .init(query: options.query,headers: ["authorization":"Bearer \(token)"],body: options.body,ifMatch: options.ifMatch,idempotencyKey: options.idempotencyKey))
    }
}
private actor LoseNotesAcknowledgement: HTTPClient {
    let client: any HTTPClient; var lost = false
    init(_ client: any HTTPClient) { self.client = client }
    func requestEntity(_ method: HTTPMethod,path: String,options: RequestOptions) async throws -> AppResult<Entity> {
        let result = try await client.requestEntity(method,path: path,options: options)
        if method == .POST && !lost { lost = true; return .failure(.unavailable(.init(message: "Acknowledgement deliberately dropped by the test."))) }; return result
    }
}
@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] != nil))
func nativeGoNotesReceiptsRecoverLostAcknowledgementAndGuardVersions() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let client = try DefaultHTTPClient.create(baseURL: base,transport: transport).get()
    let login = "notes-swift-" + UUID().uuidString.lowercased(); let password = "native integration passphrase"
    _ = try await client.requestEntity(.POST,path: "identity/register",options: .init(body: .object(["login":.string(login),"display_name":.string("Notes Swift"),"password":.string(password)]))).get()
    let grant = try await client.requestEntity(.POST,path: "identity/sign-in",options: .init(body: .object(["login":.string(login),"password":.string(password)]))).get()
    let token = try #require(grant.body.object?["access_token"]?.string), principal = try #require(grant.body.object?["principal"]?.object?["id"]?.string)
    let bearer = BearerNotesHTTP(client: client,token: token)
    let account = try await bearer.requestEntity(.POST,path: "accounts/bootstrap",options: .init(body: .object(["name":.string("Notes")]))) .get()
    let accountID = try #require(account.body.object?["id"]?.string)
    let repo = HTTPRemoteNotesRepository(client: bearer,accountID: accountID,principalID: principal)
    let lost = HTTPRemoteNotesRepository(client: LoseNotesAcknowledgement(bearer),accountID: accountID,principalID: principal)
    let create = try RemoteNoteWrite.create(commandID: UUID().uuidString.lowercased(),title: "  Café 🧱\0\n  ").get()
    if case .success = try await lost.execute(create) { Issue.record("Acknowledgement was not lost") }
    let receipt = try await repo.receipt(for: create).get()
    #expect(try await repo.execute(create).get() == receipt)
    #expect(try await repo.page().get().notes.count == 1)
    let note = try await repo.get(receipt.noteID).get(); #expect(note.title == create.title && note.version == 1)
    let update = try RemoteNoteWrite.update(commandID: UUID().uuidString.lowercased(),noteID: note.id,title: "Edited",expectedVersion: note.version).get()
    #expect(try await repo.execute(update).get().version == 2)
    let stale = try RemoteNoteWrite.update(commandID: UUID().uuidString.lowercased(),noteID: note.id,title: "Stale",expectedVersion: 1).get()
    if case .failure(let failure) = try await repo.execute(stale) { #expect(failure.kind == .conflict) } else { Issue.record("Stale write admitted") }
    let delete = try RemoteNoteWrite.delete(commandID: UUID().uuidString.lowercased(),noteID: note.id,expectedVersion: 2).get()
    #expect(try await repo.execute(delete).get().version == 3)
    #expect(try await repo.receipt(for: create).get() == receipt)
    #expect(try await repo.execute(create).get() == receipt)
    #expect(try await repo.page().get().notes.isEmpty)
    let other = try await bearer.requestEntity(.POST,path: "accounts",options: .init(body: .object(["name":.string("Other")]))) .get()
    let otherRepo = HTTPRemoteNotesRepository(client: bearer,accountID: try #require(other.body.object?["id"]?.string),principalID: principal)
    if case .failure(let failure) = try await otherRepo.receipt(for: create) { #expect(failure.kind == .notFound) } else { Issue.record("Receipt leaked scope") }
    let refresh = try #require(grant.body.object?["refresh_token"]?.string)
    _ = try await client.requestEntity(.POST,path: "identity/sign-out",options: .init(body: .object(["refresh_token":.string(refresh)])))
}
