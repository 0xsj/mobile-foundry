import SwiftUI

public struct HighlightSegment: Sendable {
    public let text: String
    public let highlighted: Bool
    public init(_ text: String, highlighted: Bool = false) { self.text = text; self.highlighted = highlighted }
}

/// One native text value from supplied literal runs. No query matching, ranges, parsing or interaction.
public struct HighlightedText: View {
    @Environment(\.foundry) private var t
    private let segments: [HighlightSegment]
    private let font: Font?
    private let emphasisColor: Color?
    public init(_ segments: [HighlightSegment], font: Font? = nil, emphasisColor: Color? = nil) {
        self.segments = segments; self.font = font; self.emphasisColor = emphasisColor
    }
    public var body: some View {
        Text(attributed).font(font ?? t.typography.body).foregroundStyle(t.colors.ink.color)
            .fixedSize(horizontal: false, vertical: true).accessibilityLabel(segments.map(\.text).joined())
    }
    private var attributed: AttributedString {
        var value = AttributedString()
        for segment in segments {
            var run = AttributedString(segment.text)
            if segment.highlighted {
                run.font = (font ?? t.typography.body).weight(.semibold)
                run.foregroundColor = emphasisColor ?? t.colors.accent.color
            }
            value.append(run)
        }
        return value
    }
}
