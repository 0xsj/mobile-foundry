import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

@Test func fileDetailAdmissionRejectsUnrelatedDeletedAndUnversionedMetadata() async throws {
    actor Wire: HTTPTransport {
        let body: JSONValue; let etag: String?
        var requests: [PreparedRequest] = []
        init(_ body: JSONValue, _ etag: String?) { self.body = body; self.etag = etag }
        func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
            requests.append(request)
            return .success(WireResponse(status: 200, headers: etag.map { ["etag": $0] } ?? [:], body: try JSONEncoder().encode(body)))
        }
    }
    let account = "00000000-0000-4000-8000-000000000002", principal = "00000000-0000-4000-8000-000000000003", id = "00000000-0000-4000-8000-000000000004"
    let base: [String: JSONValue] = ["id": .string(id), "account_id": .string(account), "owner_id": .string(principal), "name": .string("File.png"), "content_type": .string("image/png"), "byte_size": .number(3), "sha256": .string(String(repeating: "a", count: 64)), "status": .string("available"), "version": .number(2)]
    for scenario in ["available", "pending", "wrong-id", "wrong-account", "deleted", "wrong-etag", "missing-etag"] {
        var body = base; var etag: String? = "\"2\""
        switch scenario {
        case "pending": body["status"] = .string("pending_upload"); body["version"] = .number(1); etag = "\"1\""
        case "wrong-id": body["id"] = .string(principal)
        case "wrong-account": body["account_id"] = .string(principal)
        case "deleted": body["status"] = .string("deleted"); body["version"] = .number(3); etag = "\"3\""
        case "wrong-etag": etag = "\"1\""
        case "missing-etag": etag = nil
        default: break
        }
        let wire = Wire(.object(body), etag)
        let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
        let repo = HTTPRemoteFilesRepository(client: client, accountID: account, principalID: principal)
        let result = try await repo.get(id: id)
        if ["available", "pending"].contains(scenario) { #expect(try result.get().id == id) }
        else if case .failure(let error) = result { #expect(error.meta.code == "files.invalid_remote_response") }
        else { Issue.record("Admitted \(scenario)") }
        for invalid in ["../escape", id + "\n", String(repeating: "0", count: 36)] {
            if case .success = try await repo.get(id: invalid) { Issue.record("Admitted invalid target") }
        }
        let requests = await wire.requests; #expect(requests.count == 1)
        #expect(requests[0].method == .GET && requests[0].url.path == "/v1/accounts/\(account)/files/\(id)")
        #expect(requests[0].headers["idempotency-key"] == nil && requests[0].headers["if-match"] == nil)
    }
}

private struct BearerFilesHTTP: HTTPClient {
    let client: any HTTPClient; let token: String
    private func options(_ o: RequestOptions) -> RequestOptions { .init(query: o.query, headers: ["authorization": "Bearer \(token)"], body: o.body, ifMatch: o.ifMatch, idempotencyKey: o.idempotencyKey, binaryBody: o.binaryBody) }
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> { try await client.requestEntity(method, path: path, options: self.options(options)) }
    func requestBytes(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<BinaryEntity> { try await client.requestBytes(method, path: path, options: self.options(options)) }
}
private actor LoseFileAcknowledgement: HTTPClient {
    let client: any HTTPClient; let suffix: String; var lost = false
    init(_ client: any HTTPClient, suffix: String) { self.client = client; self.suffix = suffix }
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        let answer = try await client.requestEntity(method, path: path, options: options)
        if path.hasSuffix(suffix) && !lost { lost = true; return .failure(.unavailable(.init(message: "Acknowledgement deliberately dropped."))) }; return answer
    }
}
@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] != nil))
func nativeGoFilesBinaryTransferAndReceiptRecovery() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let raw = try DefaultHTTPClient.create(baseURL: base, transport: transport).get()
    let login = "files-swift-" + UUID().uuidString.lowercased(); let password = "native integration passphrase"
    _ = try await raw.requestEntity(.POST, path: "identity/register", options: .init(body: .object(["login": .string(login), "display_name": .string("Files Swift"), "password": .string(password)]))).get()
    let grant = try await raw.requestEntity(.POST, path: "identity/sign-in", options: .init(body: .object(["login": .string(login), "password": .string(password)]))).get()
    let token = try #require(grant.body.object?["access_token"]?.string), principal = try #require(grant.body.object?["principal"]?.object?["id"]?.string)
    let bearer = BearerFilesHTTP(client: raw, token: token)
    let account = try await bearer.requestEntity(.POST, path: "accounts/bootstrap", options: .init(body: .object(["name": .string("Files")]))).get()
    let accountID = try #require(account.body.object?["id"]?.string)
    let repo = HTTPRemoteFilesRepository(client: bearer, accountID: accountID, principalID: principal)
    let bytes = Data([0, 255, 1, 13, 10, 128, 0])
    let spec = try UploadSpec.prepare(name: "opaque.bin", contentType: "application/octet-stream", bytes: bytes).get()
    let key = UUID().uuidString.lowercased()
    let lost = HTTPRemoteFilesRepository(client: LoseFileAcknowledgement(bearer, suffix: "/uploads"), accountID: accountID, principalID: principal)
    if case .success = try await lost.requestUpload(spec, key: key + ".request") { Issue.record("Acknowledgement was not dropped") }
    let requested = try await repo.receipt(key: key + ".request").get()
    #expect(try await repo.get(id: requested.file.id).get() == requested.file)
    #expect(try await repo.requestUpload(spec, key: key + ".request").get().file == requested.file)
    #expect(try await repo.page().get().files.count == 1)
    _ = try await repo.uploadContent(requested.file, bytes: bytes).get()
    let droppedComplete = HTTPRemoteFilesRepository(client: LoseFileAcknowledgement(bearer, suffix: "/complete"), accountID: accountID, principalID: principal)
    if case .success = try await droppedComplete.complete(requested.file, key: key + ".complete") { Issue.record("Completion acknowledgement was not dropped") }
    let available = try await repo.receipt(key: key + ".complete").get().file
    #expect(try await repo.get(id: available.id).get() == available)
    #expect(available.status == .available && available.version == 2)
    #expect(try await repo.complete(requested.file, key: key + ".complete").get().file == available)
    #expect(try await repo.download(available).get() == bytes)
    let inbox = HTTPNotificationInboxRepository(client: bearer, accountID: accountID, principalID: principal)
    var delivered: InboxNotification?
    for _ in 0..<100 {
        let page = try await inbox.page().get()
        if let item = page.items.first { #expect(page.unreadCount == 1 && page.items.count == 1); delivered = item; break }
        try await Task.sleep(for: .milliseconds(30))
    }
    let notice = try #require(delivered); #expect(notice.fileID == available.id && notice.isUnread)
    let lostRead = HTTPNotificationInboxRepository(client: LoseFileAcknowledgement(bearer, suffix: "/read"), accountID: accountID, principalID: principal)
    if case .success = try await lostRead.markRead(notice) { Issue.record("Read acknowledgement was not dropped") }
    let read = try #require(try await inbox.page().get().items.first)
    #expect(!read.isUnread && read.readAt != nil)
    #expect(try await inbox.markRead(read).get().readAt == read.readAt)
    #expect(try await inbox.page(unreadOnly: true).get().items.isEmpty)
    let lostSetting = HTTPNotificationInboxRepository(client: LoseFileAcknowledgement(bearer, suffix: "/notification-preferences"), accountID: accountID, principalID: principal)
    if case .success = try await lostSetting.setMediaEnabled(false) { Issue.record("Preference acknowledgement was not dropped") }
    #expect(try await inbox.mediaEnabled().get() == false)
    let other = try await bearer.requestEntity(.POST, path: "accounts", options: .init(body: .object(["name": .string("Other inbox")]))).get()
    let otherID = try #require(other.body.object?["id"]?.string)
    let otherInbox = HTTPNotificationInboxRepository(client: bearer, accountID: otherID, principalID: principal)
    #expect(try await otherInbox.page().get().items.isEmpty)
    #expect(try await otherInbox.mediaEnabled().get())
    _ = try await repo.delete(available, key: key + ".delete").get()
    #expect(try await repo.page().get().files.isEmpty)
    if case .failure(let value) = try await repo.get(id: available.id) { #expect(value.kind == .notFound) } else { Issue.record("Deleted metadata was returned") }
    #expect(try await repo.complete(requested.file, key: key + ".complete").get().file == available)
    if case .failure(let value) = try await repo.download(available) { #expect(value.kind == .notFound) } else { Issue.record("Deleted bytes were downloaded") }
    let refresh = try #require(grant.body.object?["refresh_token"]?.string)
    _ = try await raw.requestEntity(.POST, path: "identity/sign-out", options: .init(body: .object(["refresh_token": .string(refresh)])))
}
