import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private let commandID = "00000000-0000-4000-8000-000000000001"
private let accountID = "00000000-0000-4000-8000-000000000002"
private let principalID = "00000000-0000-4000-8000-000000000003"
private actor RemoteWire: HTTPTransport {
    let response: WireResponse
    var requests: [PreparedRequest] = []
    init(_ response: WireResponse) { self.response = response }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> { requests.append(request); return .success(response) }
}
@Test func remoteReceiptsRequireExactInputScopeAndVersionUsingSharedFixtures() async throws {
    struct Row: Decodable { let name: String; let status: Int; let etag: String?; let body: JSONValue; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let fixture = try Data(contentsOf: root.appendingPathComponent("contracts/fixtures/remote-notes/receipts.json"))
    let command = try RemoteNoteWrite.create(commandID: commandID, title: "Café").get()
    for row in try JSONDecoder().decode([Row].self, from: fixture) {
        var headers = ["x-request-id":"receipt-test"]; if let etag = row.etag { headers["etag"] = etag }
        let wire = RemoteWire(.init(status: row.status, headers: headers, body: try JSONEncoder().encode(row.body)))
        let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
        let repository = HTTPRemoteNotesRepository(client: client, accountID: accountID, principalID: principalID)
        let answer = try await repository.execute(command)
        if row.valid { #expect(try answer.get().confirms(command)) }
        else { guard case .failure(let failure) = answer else { Issue.record("Admitted \(row.name)"); continue }; #expect(failure.meta.code == "notes.invalid_remote_response"); #expect(failure.meta.requestID == "receipt-test") }
        let requests = await wire.requests; #expect(requests.count == 1)
        let request = try #require(requests.first)
        #expect(request.method == .POST && request.url.path == "/v1/accounts/\(accountID)/notes")
        #expect(request.headers["idempotency-key"] == commandID && request.headers["if-match"] == nil)
    }
}
@Test func remoteCommandsAdmitCanonicalIDsVersionsAndPreserveTitles() throws {
    #expect(try RemoteNoteWrite.create(commandID: commandID,title: "🧱\0\n").get().title == "🧱\0\n")
    for id in ["AAAAAAAA-0000-4000-8000-000000000001", "../path", "00000000-0000-0000-0000-000000000000"] { if case .success = RemoteNoteWrite.create(commandID: id,title: "Idea") { Issue.record("Bad ID admitted") } }
    for version: Int64 in [0,9_007_199_254_740_991] { if case .success = RemoteNoteWrite.delete(commandID: commandID,noteID: accountID,expectedVersion: version) { Issue.record("Bad version admitted") } }
}
