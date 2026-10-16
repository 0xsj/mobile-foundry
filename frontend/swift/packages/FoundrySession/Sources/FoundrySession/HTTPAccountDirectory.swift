import FoundryHTTP
import FoundryKernel

/// Inject a lease-bound HTTPClient; this read port owns no credentials or session state.
public struct HTTPAccountDirectory: AccountDirectory {
    private let client: any HTTPClient
    private let principalID: String
    public init(client: any HTTPClient, principalID: String) { self.client = client; self.principalID = principalID }
    public func list(cursor: String? = nil, limit: Int = 20) async throws -> AppResult<AccountPage> {
        guard GoSessionWire.uuid(principalID), (1...100).contains(limit), cursor == nil || (1...256).contains(cursor!.utf8.count) else {
            return .failure(.invalid(.init(message: "Use an admitted account page and principal.", code: "session.account_page"), fields: [:]))
        }
        var query = [QueryItem("limit", String(limit))]
        if let cursor { query.append(QueryItem("cursor", cursor)) }
        return try await client.requestEntity(.GET, path: "accounts", options: .init(query: query)).flatMap { entity in
            guard entity.metadata.status == 200, let body = entity.body.object, case .array(let rows) = body["accounts"], rows.count <= limit else { return .failure(GoSessionWire.failure(entity)) }
            let next: String?
            switch body["next_cursor"] { case .null: next = nil; case .string(let value) where (1...256).contains(value.utf8.count): next = value; default: return .failure(GoSessionWire.failure(entity)) }
            var accounts: [Account] = []
            var prior = ""
            for row in rows {
                guard let account = GoSessionWire.account(row, ownerID: principalID), account.id > prior else { return .failure(GoSessionWire.failure(entity)) }
                accounts.append(account); prior = account.id
            }
            guard next == nil || accounts.count == limit else { return .failure(GoSessionWire.failure(entity)) }
            return .success(AccountPage(accounts: accounts, nextCursor: next))
        }
    }
}
