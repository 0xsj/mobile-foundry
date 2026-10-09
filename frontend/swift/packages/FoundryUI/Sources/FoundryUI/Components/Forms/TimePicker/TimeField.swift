import SwiftUI

/// A wall-clock reading. No date, time zone, recurrence or duration is implied.
public struct ClockTime: Equatable, Sendable {
    public let hour: Int
    public let minute: Int
    public init(hour: Int, minute: Int) {
        precondition((0...23).contains(hour) && (0...59).contains(minute))
        self.hour = hour; self.minute = minute
    }
    private static var calendar: Calendar {
        var value = Calendar(identifier: .gregorian)
        value.timeZone = TimeZone(secondsFromGMT: 0)!
        return value
    }
    // A fixed UTC reference is only a bridge to the native DatePicker API.
    var pickerDate: Date { Date(timeIntervalSinceReferenceDate: Double(hour * 3600 + minute * 60)) }
    init(pickerDate: Date) {
        self.init(hour: Self.calendar.component(.hour, from: pickerDate), minute: Self.calendar.component(.minute, from: pickerDate))
    }
}

/// Native time selection with a disposable modal draft and explicit commit. Copy belongs to the caller.
public struct TimeField: View {
    @Environment(\.foundry) private var t
    private let title: String
    @Binding private var selection: ClockTime
    private let valueLabel: String
    private let confirmLabel: String
    private let cancelLabel: String
    private let enabled: Bool
    @State private var presented = false
    @State private var draft = Date(timeIntervalSinceReferenceDate: 0)
    public init(_ title: String, selection: Binding<ClockTime>, valueLabel: String,
                confirmLabel: String, cancelLabel: String, enabled: Bool = true) {
        self.title = title; self._selection = selection; self.valueLabel = valueLabel
        self.confirmLabel = confirmLabel; self.cancelLabel = cancelLabel; self.enabled = enabled
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label)
            ActionButton(variant: .secondary, enabled: enabled, action: { draft = selection.pickerDate; presented = true }) {
                Label(valueLabel, systemImage: "clock")
            }.accessibilityLabel(title).accessibilityValue(valueLabel)
        }
        .onChange(of: enabled) { _, value in if !value { presented = false } }
        .sheet(isPresented: $presented) {
            FoundryTheme(appearance: t.appearance, style: t.materials.style) {
                ScrollView {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        Text(title).font(t.typography.heading).accessibilityAddTraits(.isHeader)
                        DatePicker(title, selection: $draft, displayedComponents: .hourAndMinute)
                            .labelsHidden()
                            #if os(iOS)
                            .datePickerStyle(.wheel)
                            #endif
                            .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
                            .environment(\.calendar, Calendar(identifier: .gregorian))
                            .frame(maxWidth: .infinity)
                        ViewThatFits(in: .horizontal) {
                            HStack { actions }
                            VStack(alignment: .leading) { actions }
                        }
                    }.padding(t.space.page)
                }
            }.presentationDetents([.medium, .large])
        }
    }
    @ViewBuilder private var actions: some View {
        ActionButton(cancelLabel, variant: .secondary) { presented = false }
        ActionButton(confirmLabel, enabled: enabled) {
            guard enabled else { return }
            selection = ClockTime(pickerDate: draft); presented = false
        }
    }
}
