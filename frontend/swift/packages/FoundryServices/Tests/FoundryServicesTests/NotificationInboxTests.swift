import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private let inboxAccount = "00000000-0000-4000-8000-000000000002"
private let inboxPrincipal = "00000000-0000-4000-8000-000000000003"
private actor InboxWire: HTTPTransport {
    let body: JSONValue
    var requests: [PreparedRequest] = []
    init(_ body: JSONValue) { self.body = body }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        requests.append(request); return .success(.init(status: 200, headers: ["x-request-id":"inbox-test"], body: try JSONEncoder().encode(body)))
    }
}
@Test func inboxSharedPageAdmissionAndSingleAttempt() async throws {
    struct Row: Decodable { let name: String; let body: JSONValue; let valid: Bool; let unread: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let data = try Data(contentsOf: root.appendingPathComponent("contracts/fixtures/notifications/pages.json"))
    for row in try JSONDecoder().decode([Row].self, from: data) {
        let wire = InboxWire(row.body)
        let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
        let repo = HTTPNotificationInboxRepository(client: client, accountID: inboxAccount, principalID: inboxPrincipal)
        let answer = try await repo.page(limit: 2, unreadOnly: row.unread)
        if row.valid {
            let page = try answer.get(); #expect(page.items.count == 1)
            // Preserve microseconds beyond the old formatter's millisecond precision.
            #expect(abs(page.items[0].occurredAt.timeIntervalSince1970 - 1_792_231_200.123456) < 0.000001)
        } else {
            guard case .failure(let error) = answer else { Issue.record("Admitted \(row.name)"); continue }
            #expect(error.meta.code == "notifications.invalid_remote_response" && error.meta.requestID == "inbox-test")
        }
        let requests = await wire.requests; #expect(requests.count == 1)
        let request = try #require(requests.first)
        #expect(request.method == .GET && request.url.path == "/v1/accounts/\(inboxAccount)/notifications")
        #expect(request.headers["idempotency-key"] == nil && request.headers["if-match"] == nil)
    }
}
@Test func inboxMutationIntentAndScopeAdmission() async throws {
    let wire = InboxWire(.object(["account_id":.string(inboxAccount),"media_in_app":.bool(false)]))
    let client = try DefaultHTTPClient.create(baseURL:"https://example.invalid/v1",transport:wire).get()
    let repo = HTTPNotificationInboxRepository(client:client,accountID:inboxAccount,principalID:inboxPrincipal)
    #expect(try await repo.setMediaEnabled(false).get() == false)
    if case .success = try await repo.setMediaEnabled(true) { Issue.record("Wrong setting acknowledgement accepted") }
    let requests = await wire.requests; #expect(requests.count == 2)
    #expect(requests.allSatisfy { $0.method == .PUT && $0.headers["idempotency-key"] == nil && $0.headers["if-match"] == nil })
    let invalid = HTTPNotificationInboxRepository(client:client,accountID:"../escape",principalID:inboxPrincipal)
    if case .success = try await invalid.page() { Issue.record("Bad scope admitted") }
    if case .success = try await repo.page(cursor:"",limit:20) { Issue.record("Bad cursor admitted") }
    #expect(await wire.requests.count == 2)
}
