import SwiftUI

/// Two independently committed dates. The caller owns ordering, bounds, validation and inclusive/exclusive policy.
public struct DateRangeField: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let startLabel: String
    private let endLabel: String
    @Binding private var start: Date
    @Binding private var end: Date
    private let confirmLabel: String
    private let cancelLabel: String
    private let help: String?
    private let error: String?
    private let enabled: Bool
    public init(_ title: String, start: Binding<Date>, end: Binding<Date>, startLabel: String, endLabel: String,
                confirmLabel: String, cancelLabel: String, help: String? = nil, error: String? = nil, enabled: Bool = true) {
        self.title = title; self._start = start; self._end = end; self.startLabel = startLabel; self.endLabel = endLabel
        self.confirmLabel = confirmLabel; self.cancelLabel = cancelLabel; self.help = help; self.error = error; self.enabled = enabled
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            Text(title).font(t.typography.label).accessibilityAddTraits(.isHeader)
            DateField(startLabel, selection: $start, confirmLabel: confirmLabel, cancelLabel: cancelLabel, enabled: enabled)
            DateField(endLabel, selection: $end, confirmLabel: confirmLabel, cancelLabel: cancelLabel, enabled: enabled)
            if let help { Text(help).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
            if let error { Text(error).font(t.typography.caption).foregroundStyle(t.colors.crit.color) }
        }.foregroundStyle(t.colors.ink.color)
    }
}
