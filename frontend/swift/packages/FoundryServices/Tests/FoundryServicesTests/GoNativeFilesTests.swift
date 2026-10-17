import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

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
    #expect(try await repo.requestUpload(spec, key: key + ".request").get().file == requested.file)
    #expect(try await repo.page().get().files.count == 1)
    _ = try await repo.uploadContent(requested.file, bytes: bytes).get()
    let droppedComplete = HTTPRemoteFilesRepository(client: LoseFileAcknowledgement(bearer, suffix: "/complete"), accountID: accountID, principalID: principal)
    if case .success = try await droppedComplete.complete(requested.file, key: key + ".complete") { Issue.record("Completion acknowledgement was not dropped") }
    let available = try await repo.receipt(key: key + ".complete").get().file
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
    #expect(try await repo.complete(requested.file, key: key + ".complete").get().file == available)
    if case .failure(let value) = try await repo.download(available) { #expect(value.kind == .notFound) } else { Issue.record("Deleted bytes were downloaded") }
    let refresh = try #require(grant.body.object?["refresh_token"]?.string)
    _ = try await raw.requestEntity(.POST, path: "identity/sign-out", options: .init(body: .object(["refresh_token": .string(refresh)])))
}
