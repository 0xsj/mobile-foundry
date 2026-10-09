import FoundryUI
import SwiftUI

struct SchedulingValues {
    static let weekStart = Date(timeIntervalSince1970: 1_791_158_400) // Monday 2026-10-05, UTC fixture
    static func date(_ day: Int) -> Date { weekStart.addingTimeInterval(Double(day) * 86_400) }
    static let dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    var selected: String? = "0"
    var start = Self.date(0)
    var end = Self.date(6)
    var time = ClockTime(hour: 9, minute: 30)
    var enabled = true
    var empty = false
    var appliedDay: String?
    var appliedTime = ClockTime(hour: 9, minute: 30)
    var applied = 0
    var rangeError: String? { start > end ? "End date must be on or after start date." : nil }
    var options: [DayOption] {
        Self.dayNames.enumerated().map { index, name in
            let available = index != 6 && Self.date(index) >= start && Self.date(index) <= end
            return DayOption(id: String(index), label: String(name.prefix(3)), valueLabel: String(5 + index),
                             accessibilityLabel: "\(name), October \(5 + index), 2026\(available ? "" : ", unavailable")",
                             detail: available ? nil : "Unavailable", enabled: available)
        }
    }
    var selectedName: String { options.first { $0.id == selected }?.accessibilityLabel ?? "No day selected" }
    var canApply: Bool { enabled && rangeError == nil && options.contains { $0.id == selected && $0.enabled } }
    static func timeLabel(_ value: ClockTime) -> String { String(format: "%02d:%02d", value.hour, value.minute) }
    mutating func select(_ id: String) { guard enabled, options.contains(where: { $0.id == id && $0.enabled }) else { return }; selected = id }
    mutating func apply() {
        guard canApply else { return }
        appliedDay = selected; appliedTime = time; applied += 1
    }
    mutating func resetDates() { guard enabled else { return }; start = Self.date(0); end = Self.date(6) }
    mutating func reverseDates() { guard enabled else { return }; start = Self.date(5); end = Self.date(1) }
}
struct SchedulingExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: SchedulingValues
    var body: some View {
        Card {
            Text("Dates and agendas").font(t.typography.heading)
            Text("Pick a day, review an agenda and plan a local session.")
            NavLink("Open schedule planner", subtitle: "Day choices, date ranges and native time input") {
                SchedulingPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        SchedulingContent(values: $values)
    }
}
struct SchedulingPreview: View {
    @Binding var values: SchedulingValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            ScrollView { SchedulingContent(values: $values).padding(20) }
        }.navigationTitle("Schedule planner").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
private struct SchedulingContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: SchedulingValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable schedule controls", isOn: $values.enabled)
                ToggleField("Show empty agenda", isOn: $values.empty)
                DayStrip("October 5–11, 2026", options: values.options,
                         selection: Binding(get: { values.selected }, set: { if let id = $0 { values.select(id) } }), enabled: values.enabled)
                Text(values.selectedName).font(t.typography.caption)
            }
            Card {
                SectionHeader("Day agenda", subtitle: values.selectedName)
                if values.empty || (values.selected != "0" && (values.appliedDay == nil || values.appliedDay != values.selected)) {
                    EmptyState("Your day is open", message: "Plan a focus session when you are ready.")
                } else {
                    if values.selected == "0" {
                        AgendaRow("Design review", timeLabel: "08:00–08:30", detail: "Studio · 30 minutes",
                                  status: { Badge("Confirmed", tone: .info) }, actions: { EmptyView() })
                    }
                    if values.appliedDay != nil && values.appliedDay == values.selected {
                        Divider()
                        AgendaRow("Focus session", timeLabel: SchedulingValues.timeLabel(values.appliedTime), detail: "Personal workspace",
                                  status: { Badge("Planned locally") }, actions: { EmptyView() })
                    }
                }
            }
            Card {
                DateRangeField("Available dates", start: $values.start, end: $values.end, startLabel: "Start date", endLabel: "End date",
                               confirmLabel: "Use date", cancelLabel: "Keep date", help: "Inclusive dates. This preview uses UTC calendar dates.",
                               error: values.rangeError, enabled: values.enabled)
                    .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
                    .environment(\.calendar, Calendar(identifier: .gregorian))
                WrapLayout {
                    ActionButton("Preview reversed dates", variant: .quiet, enabled: values.enabled) { values.reverseDates() }
                    ActionButton("Reset dates", variant: .secondary, enabled: values.enabled) { values.resetDates() }
                }
            }
            Card(.floating) {
                SectionHeader("Plan a focus session", subtitle: "Your draft is separate from the session in the agenda.")
                TimeField("Session time", selection: $values.time, valueLabel: SchedulingValues.timeLabel(values.time),
                          confirmLabel: "Use time", cancelLabel: "Keep time", enabled: values.enabled)
                Text("Draft time: \(SchedulingValues.timeLabel(values.time))").font(t.typography.caption)
                ActionButton("Apply session", enabled: values.canApply) { values.apply() }
                Text("Sessions applied: \(values.applied)").font(t.typography.caption)
                Text("Local examples. No calendar access, reminders or booking service.").font(t.typography.caption)
            }
        }
    }
}
