import SwiftUI

/// Date-only selection in the caller's calendar/time zone. Cancel discards the modal draft.
public struct DateField: View {
    @Environment(\.foundry) private var tokens
    @Environment(\.calendar) private var calendar
    @Environment(\.timeZone) private var timeZone
    @Environment(\.locale) private var locale
    private let title: String
    @Binding private var selection: Date
    private let confirmLabel: String
    private let cancelLabel: String
    private let enabled: Bool
    @State private var presented = false
    @State private var draft = Date(timeIntervalSince1970: 0)
    public init(_ title: String, selection: Binding<Date>, confirmLabel: String, cancelLabel: String, enabled: Bool = true) {
        self.title = title; self._selection = selection; self.confirmLabel = confirmLabel
        self.cancelLabel = cancelLabel; self.enabled = enabled
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.inline) {
            Text(title).font(tokens.typography.label)
            ActionButton(variant: .secondary, enabled: enabled, action: { draft = selection; presented = true }) {
                Label(formatted(.abbreviated), systemImage: "calendar")
            }.accessibilityLabel(title).accessibilityValue(formatted(.complete))
        }
        .sheet(isPresented: $presented) {
            FoundryTheme {
                ScrollView { VStack(alignment: .leading, spacing: tokens.space.section) {
                    Text(title).font(tokens.typography.heading).accessibilityAddTraits(.isHeader)
                    DatePicker(title, selection: $draft, displayedComponents: .date).datePickerStyle(.graphical)
                    ViewThatFits(in: .horizontal) {
                        HStack { actions }
                        VStack(alignment: .leading) { actions }
                    }
                }.padding(tokens.space.page) }
            }
        }
    }
    private func formatted(_ style: Date.FormatStyle.DateStyle) -> String {
        selection.formatted(Date.FormatStyle(date: style, time: .omitted, locale: locale, calendar: calendar, timeZone: timeZone))
    }
    @ViewBuilder private var actions: some View {
        ActionButton(cancelLabel, variant: .secondary) { presented = false }
        ActionButton(confirmLabel) { selection = draft; presented = false }
    }
}
