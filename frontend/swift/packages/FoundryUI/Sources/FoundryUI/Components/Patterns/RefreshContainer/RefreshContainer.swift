import SwiftUI

/// Attach native refresh to a supported scrolling child, usually List. Await the caller's work for the spinner's lifetime.
/// The host owns bounded layout, concurrency, failure presentation and cancellation policy.
public struct RefreshContainer<Content: View>: View {
    private let action: @Sendable () async -> Void
    private let content: Content
    public init(onRefresh: @escaping @Sendable () async -> Void, @ViewBuilder content: () -> Content) {
        self.action = onRefresh; self.content = content()
    }
    public var body: some View { content.refreshable(action: action) }
}
