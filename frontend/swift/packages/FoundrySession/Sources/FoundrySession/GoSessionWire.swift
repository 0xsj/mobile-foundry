import Foundation
import FoundryHTTP
import FoundryKernel

enum GoSessionWire {
    struct Tokens: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
        let principal: Principal
        let access: String
        let refresh: String
        let expiry: Int64
        var description: String { "GoTokens(<redacted>)" }; var debugDescription: String { description }
    }
    static func uuid(_ value: String) -> Bool {
        value.utf8.count == 36 && value != "00000000-0000-0000-0000-000000000000" &&
        value.range(of: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", options: .regularExpression) != nil
    }
    static func name(_ value: String) -> Bool {
        guard case .success(let name) = AccountName.make(value) else { return false }
        return name.value.utf8.elementsEqual(value.utf8)
    }
    static func token(_ value: String, prefix: String) -> Bool {
        value.utf8.count == 46 && value.range(of: "^\(prefix)\\.[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil
    }
    static func version(_ value: JSONValue?) -> UInt64? {
        guard case .number(let n) = value, n >= 1, n <= 9_007_199_254_740_991, n.rounded(.towardZero) == n else { return nil }
        return UInt64(n)
    }
    static func millis(_ value: String) -> Int64? {
        micros(value).map { $0 / 1000 }
    }
    static func micros(_ value: String) -> Int64? {
        guard (20...27).contains(value.utf8.count), value.hasSuffix("Z"), value.range(of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?Z$", options: .regularExpression) != nil,
              let year = Int(value.prefix(4)), year >= 1970 else { return nil }
        let prefix = String(value.prefix(19))
        let format = DateFormatter()
        format.locale = Locale(identifier: "en_US_POSIX"); format.timeZone = TimeZone(secondsFromGMT: 0)
        format.calendar = Calendar(identifier: .gregorian); format.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"; format.isLenient = false
        guard let date = format.date(from: prefix), format.string(from: date) == prefix else { return nil }
        let fraction = value.dropFirst(19).dropLast()
        let microseconds = fraction.isEmpty ? 0 : Int(String(fraction.dropFirst()).padding(toLength: 6, withPad: "0", startingAt: 0))!
        return Int64(date.timeIntervalSince1970) * 1_000_000 + Int64(microseconds)
    }
    static func failure(_ entity: Entity) -> Failure {
        .internalError(.init(message: "The Go API returned an unexpected session or account response.", code: "session.go_invalid_response",
                            requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID))
    }
    static func tokens(_ entity: Entity, now: Int64) -> Tokens? {
        guard entity.metadata.status == 200, let body = entity.body.object, body["authority_id"]?.string == "go.identity.v1",
              let p = body["principal"]?.object, let pid = p["id"]?.string, uuid(pid),
              let login = p["login"]?.string, login.utf8.allSatisfy({ $0 >= 33 && $0 <= 126 }), login.range(of: "^[a-z0-9][a-z0-9._@+-]{2,127}$", options: .regularExpression) != nil,
              let display = p["display_name"]?.string, name(display), let principal = Principal(id: pid, displayName: display),
              let sid = body["session_id"]?.string, uuid(sid), version(body["session_version"]) != nil,
              let access = body["access_token"]?.string, token(access, prefix: "a1"),
              let refresh = body["refresh_token"]?.string, token(refresh, prefix: "r1"),
              let expiry = body["access_expires_at"]?.string.flatMap(millis), expiry > now,
              let absolute = body["refresh_expires_at"]?.string.flatMap(millis), absolute >= expiry else { return nil }
        return Tokens(principal: principal, access: access, refresh: refresh, expiry: expiry)
    }
    static func account(_ body: JSONValue, ownerID: String) -> Account? {
        guard let body = body.object, let identity = body["id"]?.string, uuid(identity), body["owner_id"]?.string == ownerID,
              let name = body["name"]?.string, self.name(name), version(body["version"]) != nil,
              let created = body["created_at"]?.string.flatMap(micros), let updated = body["updated_at"]?.string.flatMap(micros), updated >= created else { return nil }
        return Account(id: identity, name: name)
    }
}
