import SwiftUI

// Scale before subtraction so a finite range spanning -Double.max...Double.max cannot overflow.
func normalizedSparkline(_ values: [Double]) -> [CGPoint] {
    precondition(values.allSatisfy(\.isFinite))
    guard !values.isEmpty else { return [] }
    let scale = values.map { abs($0) }.max() ?? 0
    let scaled = scale == 0 ? values : values.map { $0 / scale }
    let low = scaled.min()!, high = scaled.max()!, span = high - low
    return scaled.enumerated().map { index, value in
        CGPoint(x: values.count == 1 ? 0.5 : Double(index) / Double(values.count - 1),
                y: span == 0 ? 0.5 : 1 - (value - low) / span)
    }
}
/// Small equally-spaced sample sequence, physical left to right. No time axis, interpolation, selection or data loading.
public struct Sparkline: View {
    @Environment(\.foundry) private var t
    private let points: [CGPoint]
    private let summary: String
    private let height: CGFloat
    private let lineWidth: CGFloat
    private let color: Color?
    public init(_ values: [Double], summary: String, height: CGFloat = 64, lineWidth: CGFloat = 3, color: Color? = nil) {
        precondition(height.isFinite && height > 0 && lineWidth.isFinite && lineWidth > 0 && height > lineWidth)
        points = normalizedSparkline(values); self.summary = summary; self.height = height; self.lineWidth = lineWidth; self.color = color
    }
    public var body: some View {
        Canvas { context, size in
            let inset = min(lineWidth / 2, min(size.width, size.height) / 2)
            let coordinates = points.map { CGPoint(x: inset + $0.x * max(0, size.width - 2 * inset),
                                                   y: inset + $0.y * max(0, size.height - 2 * inset)) }
            if coordinates.count == 1, let point = coordinates.first {
                context.fill(Path(ellipseIn: CGRect(x: point.x - inset, y: point.y - inset, width: inset * 2, height: inset * 2)),
                             with: .color(color ?? t.colors.accent.color))
            } else if let first = coordinates.first {
                var path = Path(); path.move(to: first)
                for point in coordinates.dropFirst() { path.addLine(to: point) }
                context.stroke(path, with: .color(color ?? t.colors.accent.color),
                               style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
            }
        }.frame(height: height).frame(maxWidth: .infinity)
            .accessibilityElement(children: .ignore).accessibilityLabel(summary)
    }
}
