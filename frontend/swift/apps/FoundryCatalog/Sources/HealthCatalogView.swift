import SwiftUI
import OSLog
import FoundryKernel
import FoundryHTTP
import FoundryServices

private enum HealthScenario: String, CaseIterable, Identifiable, Sendable {
    case healthy = "Healthy", unavailable = "Unavailable", malformed = "Malformed", validation = "Validation", rateLimited = "Rate limited", timeout = "Timeout"
    var id: Self { self }
}
private struct CatalogTransport: HTTPTransport {
    let scenario: HealthScenario
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        try await Task.sleep(for: .milliseconds(scenario == .timeout ? 1_000 : 250))
        let status: Int
        let body: String
        var headers = ["x-request-id": "catalog-request", "etag": "\"catalog-v1\""]
        switch scenario {
        case .healthy, .timeout: status = 200; body = "{\"status\":\"ok\"}"
        case .unavailable: status = 503; body = "{\"detail\":\"The service is temporarily unavailable.\"}"
        case .malformed: status = 200; body = "{"
        case .validation: status = 422; body = "{\"detail\":\"Check the supplied input.\",\"fields\":{\"example\":\"Required.\"}}"
        case .rateLimited: status = 429; body = "{\"detail\":\"Too many requests.\"}"; headers["retry-after"] = "2"
        }
        return .success(.init(status: status, headers: headers, body: Data(body.utf8)))
    }
}
private enum HealthPhase {
    case loading
    case success(Health)
    case failure(Failure)
}

struct HealthCatalogView: View {
    @State private var scenario: HealthScenario = .healthy
    @State private var generation = 0
    @State private var phase: HealthPhase = .loading
    private let logger = Logger(subsystem: "dev.mobilefoundry.catalog", category: "health")

    var body: some View {
        List {
            Section {
                Picker("Response", selection: $scenario) {
                    ForEach(HealthScenario.allCases) { Text($0.rawValue).tag($0) }
                }
                Button("Run again") { generation += 1 }
            } footer: {
                Text("Responses are injected locally. No backend is needed. Changing the example cancels the previous request.")
            }
            Section("Readiness") {
                switch phase {
                case .loading: ProgressView("Checking health…")
                case .success(let health):
                    Label("Healthy", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                    Text("Status: \(health.status.rawValue)")
                    if let id = health.requestID { Text("Request: \(id)").font(.caption) }
                    if let version = health.version { Text("Version: \(version)").font(.caption) }
                case .failure(let failure):
                    let info = failure.publicInfo()
                    Label(title(for: info.kind), systemImage: "exclamationmark.circle")
                    Text(info.meta.message)
                    if case .invalid(_, let fields) = info {
                        ForEach(fields.keys.sorted(), id: \.self) { Text("\($0): \(fields[$0] ?? "")").font(.caption) }
                    }
                    if case .rateLimited(_, let delay) = info, let delay {
                        Text("Retry timing: \(delay.milliseconds) ms").font(.caption)
                    }
                    if let id = info.meta.requestID { Text("Request: \(id)").font(.caption) }
                }
            }
        }
        .navigationTitle("HTTP health")
        .task(id: "\(scenario.rawValue)-\(generation)") { await load() }
    }

    @MainActor private func load() async {
        phase = .loading
        do {
            let http = try DefaultHTTPClient.create(baseURL: "https://catalog.invalid/v1", transport: CatalogTransport(scenario: scenario)).get()
            let answer = try await HealthService(http: http).ready(options: .init(timeoutMilliseconds: scenario == .timeout ? 100 : 15_000, correlationID: "catalog-correlation"))
            try Task.checkCancellation()
            switch answer {
            case .success(let health): phase = .success(health)
            case .failure(let failure): phase = .failure(failure)
            }
        } catch is CancellationError {
            // The view task owns this request; replacement/removal is silent.
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("Unexpected health error: \(String(reflecting: error), privacy: .private)")
            phase = .failure(.internalError(.init(message: "An unexpected error occurred.")))
        }
    }

    private func title(for kind: FailureKind) -> String {
        switch kind {
        case .unauthenticated: "Sign in required"
        case .forbidden: "Access denied"
        case .rateLimited: "Please wait"
        case .unavailable: "Service unavailable"
        case .timeout: "Request timed out"
        case .canceled: "Request canceled"
        case .internalError: "Unexpected response"
        case .notFound: "Not found"
        case .invalid: "Check input"
        case .conflict: "Data changed"
        }
    }
}
