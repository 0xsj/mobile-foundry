import SwiftUI

public struct DayOption: Identifiable, Equatable, Sendable {
    public let id: String
    public let label: String
    public let valueLabel: String
    public let accessibilityLabel: String
    public let detail: String?
    public let enabled: Bool
    public init(id: String, label: String, valueLabel: String, accessibilityLabel: String, detail: String? = nil, enabled: Bool = true) {
        self.id = id; self.label = label; self.valueLabel = valueLabel; self.accessibilityLabel = accessibilityLabel
        self.detail = detail; self.enabled = enabled
    }
}

/// A small caller-supplied set of days. Wraps at narrow widths; does not generate a calendar or choose a default.
public struct DayStrip: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let options: [DayOption]
    @Binding private var selection: String?
    private let enabled: Bool
    public init(_ title: String, options: [DayOption], selection: Binding<String?>, enabled: Bool = true) {
        precondition(Set(options.map(\.id)).count == options.count)
        self.title = title; self.options = options; self._selection = selection; self.enabled = enabled
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label).accessibilityAddTraits(.isHeader)
            WrapLayout {
                ForEach(options) { day in
                    Button { selection = day.id } label: {
                        VStack(spacing: t.space.inline) {
                            Text(day.label).font(t.typography.caption)
                            Text(day.valueLabel).font(t.typography.heading)
                            if let detail = day.detail { Text(detail).font(t.typography.caption) }
                        }.padding(t.space.inline).frame(minWidth: t.shape.minimumInteractive, minHeight: t.shape.minimumInteractive)
                            .foregroundStyle(t.colors.ink.color)
                            .background(selection == day.id ? t.colors.accentTint.color : t.colors.surfacePanel.color,
                                        in: RoundedRectangle(cornerRadius: t.shape.radii[2]))
                            .overlay(RoundedRectangle(cornerRadius: t.shape.radii[2]).stroke(selection == day.id ? t.colors.accent.color : t.colors.line.color))
                    }.buttonStyle(.plain).disabled(!enabled || !day.enabled).opacity(enabled && day.enabled ? 1 : 0.5)
                        .accessibilityLabel(day.accessibilityLabel).accessibilityAddTraits(selection == day.id ? .isSelected : [])
                }
            }
        }
    }
}
