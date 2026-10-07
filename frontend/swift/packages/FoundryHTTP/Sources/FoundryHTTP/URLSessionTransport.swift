import Foundation
import FoundryKernel

/// Owns a session without cookies, credentials, redirects, or response caching.
public final class URLSessionHTTPTransport: HTTPTransport {
    private let session: URLSession
    private let delegate: SessionDelegate

    public init(configuration: URLSessionConfiguration = .ephemeral,
                onDiagnostic: @escaping @Sendable (any Error) -> Void = { _ in }) {
        let configuration = configuration.copy() as! URLSessionConfiguration
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.urlCache = nil
        self.delegate = SessionDelegate(onDiagnostic: onDiagnostic)
        self.session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
    }

    deinit { session.invalidateAndCancel() }

    public func invalidate() { session.invalidateAndCancel() }

    public func send(_ prepared: PreparedRequest) async throws -> AppResult<WireResponse> {
        let call = SessionCall()
        return try await withTaskCancellationHandler {
            try Task.checkCancellation()
            return try await withCheckedThrowingContinuation { continuation in
                var request = URLRequest(url: prepared.url, cachePolicy: .reloadIgnoringLocalCacheData)
                request.httpMethod = prepared.method.rawValue
                request.httpBody = prepared.body
                request.allHTTPHeaderFields = prepared.headers
                let task = session.dataTask(with: request)
                delegate.register(task: task, correlationID: prepared.headers["x-correlation-id"], continuation: continuation)
                call.start(task)
            }
        } onCancel: {
            call.cancel()
        }
    }
}

/// The lock protects install/cancel races before a URLSession task exists.
private final class SessionCall: @unchecked Sendable {
    private let lock = NSLock()
    private var task: URLSessionDataTask?
    private var canceled = false

    func start(_ task: URLSessionDataTask) {
        lock.lock(); self.task = task; let canceled = canceled; lock.unlock()
        if canceled { task.cancel() }
        task.resume()
    }

    func cancel() {
        lock.lock(); canceled = true; let task = task; lock.unlock()
        task?.cancel()
    }
}

/// Pending state is accessed only under lock; continuations are resumed outside it.
private final class SessionDelegate: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private struct Pending {
        let continuation: CheckedContinuation<AppResult<WireResponse>, any Error>
        let correlationID: String?
        var response: HTTPURLResponse?
        var body = Data()
    }
    private let lock = NSLock()
    private var pending: [Int: Pending] = [:]
    private let onDiagnostic: @Sendable (any Error) -> Void

    init(onDiagnostic: @escaping @Sendable (any Error) -> Void) { self.onDiagnostic = onDiagnostic }

    func register(task: URLSessionDataTask, correlationID: String?, continuation: CheckedContinuation<AppResult<WireResponse>, any Error>) {
        lock.lock(); pending[task.taskIdentifier] = Pending(continuation: continuation, correlationID: correlationID); lock.unlock()
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping @Sendable (URLSession.ResponseDisposition) -> Void) {
        lock.lock(); pending[dataTask.taskIdentifier]?.response = response as? HTTPURLResponse; lock.unlock()
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock(); pending[dataTask.taskIdentifier]?.body.append(data); lock.unlock()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        completionHandler(nil)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: (any Error)?) {
        lock.lock(); let state = pending.removeValue(forKey: task.taskIdentifier); lock.unlock()
        guard let state else { return }
        let continuation = state.continuation
        if let error {
            guard let network = error as? URLError else { continuation.resume(throwing: error); return }
            if network.code == .cancelled { continuation.resume(throwing: CancellationError()); return }
            onDiagnostic(error)
            if let response = state.response ?? task.response as? HTTPURLResponse {
                continuation.resume(returning: .success(wire(response, body: nil)))
                return
            }
            let meta = FailureMeta(message: "The request could not reach a usable response.", correlationID: state.correlationID)
            switch network.code {
            case .timedOut: continuation.resume(returning: .failure(.timeout(meta)))
            case .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed, .networkConnectionLost,
                 .notConnectedToInternet, .resourceUnavailable, .secureConnectionFailed,
                 .serverCertificateHasBadDate, .serverCertificateUntrusted,
                 .serverCertificateHasUnknownRoot, .serverCertificateNotYetValid,
                 .clientCertificateRejected, .clientCertificateRequired, .cannotLoadFromNetwork:
                continuation.resume(returning: .failure(.unavailable(meta)))
            default: continuation.resume(throwing: error)
            }
            return
        }
        guard let response = state.response ?? task.response as? HTTPURLResponse else {
            continuation.resume(returning: .failure(internalFailure("http.unreadable_response", "The response is not readable.", correlationID: state.correlationID)))
            return
        }
        continuation.resume(returning: .success(wire(response, body: state.body)))
    }

    private func wire(_ response: HTTPURLResponse, body: Data?) -> WireResponse {
        .init(status: response.statusCode,
              headers: Dictionary(uniqueKeysWithValues: response.allHeaderFields.map { (String(describing: $0.key), String(describing: $0.value)) }), body: body)
    }
}
