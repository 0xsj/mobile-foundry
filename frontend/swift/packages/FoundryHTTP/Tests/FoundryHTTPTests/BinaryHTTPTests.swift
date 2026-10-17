import Foundation
import FoundryKernel
import Testing
@testable import FoundryHTTP

@Test func binaryBodiesAndResponsesRetainBytesAndRejectAmbiguousBodies() async throws {
    let bytes = Data([0, 255, 1, 128])
    let transport = StubTransport(.init(status: 200, headers: ["content-type": "image/png", "etag": "\"2\""], body: bytes))
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: transport).get()
    let body = BinaryBody(bytes, contentType: "image/png")
    let result = try await client.requestBytes(.PUT, path: "files/content", options: .init(binaryBody: body)).get()
    #expect(result.bytes == bytes && result.contentType == "image/png")
    let requests = await transport.requests
    #expect(requests.count == 1 && requests[0].body == bytes && requests[0].headers["content-type"] == "image/png")
    if case .success = try await client.requestEntity(.PUT, path: "files/content", options: .init(body: .object([:]), binaryBody: body)) { Issue.record("Ambiguous body admitted") }
    if case .success = try await client.requestEntity(.GET, path: "files/content", options: .init(binaryBody: body)) { Issue.record("GET body admitted") }
    #expect(await transport.requests.count == 1)
    let bad = StubTransport(.init(status: 403, body: Data(#"{"type":"urn:problem:forbidden","status":403,"title":"Forbidden","kind":"forbidden","code":"files.account_forbidden"}"#.utf8)))
    let denied = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: bad).get()
    if case .failure(let error) = try await denied.requestBytes(.GET, path: "files/content") { #expect(error.kind == .forbidden) } else { Issue.record("HTTP failure became bytes") }
}
