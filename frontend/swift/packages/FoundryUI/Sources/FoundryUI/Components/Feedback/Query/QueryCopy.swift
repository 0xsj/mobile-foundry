import Foundation

/// Caller-selected copy; pass localized strings from the owning application.
public struct QueryCopy: Sendable {
    public let idle: String
    public let loading: String
    public let refreshing: String
    public let empty: String
    public let refresh: String
    public let retry: String
    public let cancel: String

    public init(idle: String, loading: String, refreshing: String, empty: String,
                refresh: String = "Refresh", retry: String = "Retry", cancel: String = "Cancel loading") {
        self.idle = idle
        self.loading = loading
        self.refreshing = refreshing
        self.empty = empty
        self.refresh = refresh
        self.retry = retry
        self.cancel = cancel
    }
}

