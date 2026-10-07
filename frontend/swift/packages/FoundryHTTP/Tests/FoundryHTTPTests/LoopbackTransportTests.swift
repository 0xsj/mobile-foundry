import Darwin
import Foundation
import Testing
import FoundryHTTP
import FoundryKernel

/// One real TCP exchange so URLProtocol's synthetic failure delivery cannot hide headers.
private final class LoopbackServer: @unchecked Sendable {
    let port: UInt16
    private let socketFD: Int32
    private let lock = NSLock()
    private var exchanges = 0
    var count: Int { lock.lock(); defer { lock.unlock() }; return exchanges }
    init(response: String) throws {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { throw POSIXError(.EIO) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_addr.s_addr = inet_addr("127.0.0.1")
        let bound = withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard bound == 0, listen(fd, 1) == 0 else { close(fd); throw POSIXError(.EIO) }
        var length = socklen_t(MemoryLayout<sockaddr_in>.size)
        let named = withUnsafeMutablePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(fd, $0, &length) }
        }
        guard named == 0 else { close(fd); throw POSIXError(.EIO) }
        socketFD = fd; port = UInt16(bigEndian: address.sin_port)
        DispatchQueue.global().async { [self] in
            let connection = accept(fd, nil, nil)
            guard connection >= 0 else { return }
            defer { close(connection) }
            var timeout = timeval(tv_sec: 2, tv_usec: 0)
            setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO, &timeout, socklen_t(MemoryLayout<timeval>.size))
            var noSignal: Int32 = 1
            setsockopt(connection, SOL_SOCKET, SO_NOSIGPIPE, &noSignal, socklen_t(MemoryLayout<Int32>.size))
            var buffer = [UInt8](repeating: 0, count: 8192)
            guard recv(connection, &buffer, buffer.count, 0) > 0 else { return }
            lock.lock(); exchanges += 1; lock.unlock()
            let bytes = Array(response.utf8)
            bytes.withUnsafeBytes { data in
                var sent = 0
                while sent < data.count {
                    let amount = Darwin.send(connection, data.baseAddress!.advanced(by: sent), data.count - sent, 0)
                    if amount <= 0 { break }; sent += amount
                }
            }
        }
    }
    func stop() { shutdown(socketFD, SHUT_RDWR); close(socketFD) }
}

struct LoopbackTransportTests {
    @Test func brokenBodyKeepsReceivedStatusAndIDs() async throws {
        let server = try LoopbackServer(response: "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 99\r\nX-Request-ID: loopback-id\r\nConnection: close\r\n\r\n{")
        defer { server.stop() }
        let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
        let http = try DefaultHTTPClient.create(baseURL: "http://127.0.0.1:\(server.port)/v1", transport: transport, timeoutMilliseconds: 2_000).get()
        guard case .failure(let failure) = try await http.request(.GET, path: "health/live") else { Issue.record("Broken response admitted"); return }
        #expect(failure.kind == .unavailable)
        #expect(failure.meta.requestID == "loopback-id")
        #expect(server.count == 1)
    }
    @Test func redirectIsReturnedWithoutFollowing() async throws {
        let server = try LoopbackServer(response: "HTTP/1.1 302 Found\r\nLocation: /elsewhere\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        defer { server.stop() }
        let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
        let http = try DefaultHTTPClient.create(baseURL: "http://127.0.0.1:\(server.port)/v1", transport: transport, timeoutMilliseconds: 500).get()
        guard case .failure(let failure) = try await http.request(.GET, path: "health/live") else { Issue.record("Redirect admitted"); return }
        #expect(failure.kind == .internalError)
        #expect(server.count == 1)
    }
}
