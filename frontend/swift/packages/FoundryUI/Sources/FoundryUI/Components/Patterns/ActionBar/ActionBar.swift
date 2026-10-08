import SwiftUI

/// Floating action region with optional supplied summary. Its host owns placement and operations.
public struct ActionBar<Actions: View>: View {
    @Environment(\.foundry) private var t
    private let summary: String?
    private let actions: Actions
    public init(summary: String? = nil, @ViewBuilder actions: () -> Actions) {
        self.summary = summary; self.actions = actions()
    }
    public var body: some View {
        Card(.floating) {
            if let summary { Text(summary).font(t.typography.caption) }
            actions
        }
    }
}
