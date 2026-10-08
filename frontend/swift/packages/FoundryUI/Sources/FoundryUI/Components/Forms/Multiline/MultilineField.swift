import SwiftUI

/// Native growing input with a bounded visible line range; text is never truncated by this component.
public struct MultilineField: View {
    @Environment(\.foundry) private var t
    @Binding private var text: String
    private let title: String
    private let help: String?
    private let error: String?
    private let enabled: Bool
    private let lines: ClosedRange<Int>
    private let focus: FocusState<Bool>.Binding
    public init(_ title: String, text: Binding<String>, help: String? = nil, error: String? = nil,
                enabled: Bool = true, lines: ClosedRange<Int> = 3...6, focus: FocusState<Bool>.Binding) {
        precondition(lines.lowerBound > 0)
        self.title = title; self._text = text; self.help = help; self.error = error
        self.enabled = enabled; self.lines = lines; self.focus = focus
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label).accessibilityHidden(true)
            TextField(title, text: $text, axis: .vertical).lineLimit(lines)
                .textFieldStyle(.roundedBorder).font(t.typography.body).focused(focus).disabled(!enabled)
                .accessibilityLabel(title).accessibilityHint(error ?? help ?? "")
            if let error {
                Label(error, systemImage: "exclamationmark.circle").font(t.typography.caption)
                    .foregroundStyle(t.colors.crit.color).accessibilityLabel("Error: \(error)")
            } else if let help { Text(help).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }
    }
}
