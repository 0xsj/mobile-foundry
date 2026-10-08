import SwiftUI

/// Caller-controlled disclosure for known long copy. This does not measure overflow or store expansion state.
public struct ExpandableText: View {
    @Environment(\.foundry) private var t
    private let text: String
    @Binding private var expanded: Bool
    private let collapsedLines: Int
    private let moreLabel: String
    private let lessLabel: String
    public init(_ text: String, expanded: Binding<Bool>, moreLabel: String, lessLabel: String, collapsedLines: Int = 3) {
        precondition(collapsedLines > 0)
        self.text = text; self._expanded = expanded; self.collapsedLines = collapsedLines
        self.moreLabel = moreLabel; self.lessLabel = lessLabel
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(text).lineLimit(expanded ? nil : collapsedLines).fixedSize(horizontal: false, vertical: true)
            Button(expanded ? lessLabel : moreLabel) { expanded.toggle() }
                .buttonStyle(.plain).foregroundStyle(t.colors.accent.color)
                .padding(.vertical, 10).frame(minHeight: 44)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
