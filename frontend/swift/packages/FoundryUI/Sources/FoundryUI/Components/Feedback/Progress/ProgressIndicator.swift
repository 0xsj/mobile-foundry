import SwiftUI

/// nil is indeterminate. Finite progress is clamped to 0...1; nonfinite input is indeterminate.
public struct ProgressIndicator: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let fraction: Double?
    public init(_ title: String, fraction: Double? = nil) {
        self.title = title
        self.fraction = fraction.flatMap { $0.isFinite ? min(1, max(0, $0)) : nil }
    }
    public var body: some View {
        ProgressView(value: fraction) { Text(title).font(tokens.typography.label) }
            .tint(tokens.colors.accent.color)
    }
}
