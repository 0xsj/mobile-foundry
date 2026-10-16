import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Observation

enum ProbeResponse: String, CaseIterable, Identifiable {
    case success = "Success", unauthorized = "Unauthorized", slow = "Slow response"
    var id: Self { self }
}
/// A local wire fixture exercises the real request and problem-decoding boundary, without a server.
private struct ProbeTransport: HTTPTransport {
    let response: ProbeResponse
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        if response == .slow { try await Task.sleep(for: .seconds(3)) }
        if response == .unauthorized {
            return .success(.init(status: 401, headers: ["content-type": "application/problem+json", "x-request-id": "catalog-probe"],
                body: Data(#"{"type":"about:blank","title":"Unauthorized","status":401,"detail":"This local fixture rejected the request."}"#.utf8)))
        }
        return .success(.init(status: 200, headers: ["content-type": "application/json", "x-request-id": "catalog-probe"], body: Data(#"{"status":"ok"}"#.utf8)))
    }
}
@MainActor @Observable final class AuthenticatedProbeStore {
    private(set) var report: String?
    private(set) var isBusy = false
    @ObservationIgnored private let session: SessionController
    @ObservationIgnored private let lease: SessionLease
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var closed = false
    init(session: SessionController, lease: SessionLease) { self.session = session; self.lease = lease }
    @discardableResult func run(_ response: ProbeResponse) -> Task<Void, Never>? {
        guard !closed && !isBusy && session.isCurrent(lease) else { return nil }
        generation += 1; let current = generation; isBusy = true; report = nil
        let task = Task {
            defer { if current == generation { isBusy = false; self.task = nil } }
            do {
                let client = try AuthenticatedHTTPClient.create(baseURL: "https://catalog.example.test/api", transport: ProbeTransport(response: response), session: session, lease: lease).get()
                let answer = try await client.requestEntity(.GET, path: "probe", options: .init(correlationID: "catalog-probe"))
                guard current == generation && session.isCurrent(lease) else { return }
                switch answer {
                case .success(let entity): report = "GET /api/probe · \(entity.metadata.status) · \(lease.scope.accountID) · request \(entity.metadata.requestID ?? "none")"
                case .failure(let failure): report = failure.publicInfo().meta.message
                }
            } catch {
                guard current == generation && session.isCurrent(lease) && !Task.isCancelled else { return }
                report = "The local probe could not complete."
            }
        }
        self.task = task; return task
    }
    func shutdown() { closed = true; generation += 1; task?.cancel(); task = nil; report = nil; isBusy = false }
}
