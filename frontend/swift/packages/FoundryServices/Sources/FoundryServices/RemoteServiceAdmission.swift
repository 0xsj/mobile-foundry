import Foundation

/// Shared wire identifier/calendar admission, independent of any service domain.
enum RemoteServiceAdmission {
    static func uuid(_ value: String) -> Bool { value != "00000000-0000-0000-0000-000000000000" && value.range(of: "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\z", options: .regularExpression) != nil }
    static func date(_ value: Date) -> Bool { value.timeIntervalSince1970.isFinite && value.timeIntervalSince1970 >= 0 && value.timeIntervalSince1970 < 253402300800 }
    static func timestamp(_ value: String?) -> Date? {
        guard let value, value.range(of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?Z\\z", options: .regularExpression) != nil else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let whole = String(value.prefix(19)) + "Z"
        guard let seconds = formatter.date(from: whole), formatter.string(from: seconds) == whole else { return nil }
        let fraction = value.contains(".") ? Double("0." + value.dropFirst(20).dropLast()) : 0
        guard let fraction else { return nil }
        let parsed = seconds.addingTimeInterval(fraction)
        return date(parsed) ? parsed : nil
    }
}
