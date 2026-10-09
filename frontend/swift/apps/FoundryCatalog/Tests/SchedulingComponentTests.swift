@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func scheduleDraftAdmissionKeepsCommittedSessionsThroughInvalidAndUnavailableChoices() {
    var values = SchedulingValues()
    values.apply()
    #expect(values.applied == 1 && values.appliedDay == "0" && values.appliedTime == ClockTime(hour: 9, minute: 30))
    values.time = ClockTime(hour: 10, minute: 45)
    #expect(values.appliedTime == ClockTime(hour: 9, minute: 30))
    values.select("6"); values.select("unknown")
    #expect(values.selected == "0")
    values.reverseDates(); values.apply()
    #expect(!values.canApply && values.rangeError != nil && values.applied == 1)
    values.resetDates(); values.select("1"); values.apply()
    #expect(values.applied == 2 && values.appliedDay == "1" && values.appliedTime == values.time)
    values.enabled = false; values.select("0"); values.apply(); values.reverseDates()
    #expect(values.selected == "1" && values.applied == 2 && values.rangeError == nil)
    values.enabled = true; values.selected = nil; values.apply()
    #expect(!values.canApply && values.applied == 2)
    values.selected = "0"; values.start = SchedulingValues.date(2)
    #expect(!values.canApply && values.selected == "0") // Bounds do not silently choose another day.
}
@Observable @MainActor private final class ScheduleProbeValues {
    var textSize: DynamicTypeSize = .large
    var selected: String? = "mon"
    var start = SchedulingValues.date(0)
    var end = SchedulingValues.date(6)
    var time = ClockTime(hour: 9, minute: 30)
    var frames: [String: CGRect] = [:]
}
private struct ScheduleFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func scheduleFrame(_ name: String) -> some View {
        background { GeometryReader { geometry in Color.clear.preference(key: ScheduleFrames.self, value: [name: geometry.frame(in: .named("schedule-probe"))]) } }
    }
}
private struct ScheduleProbe: View {
    @Bindable var values: ScheduleProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    DayStrip("Choose a day", options: [
                        DayOption(id: "mon", label: "Monday", valueLabel: "5", accessibilityLabel: "Monday, October 5"),
                        DayOption(id: "tue", label: "Tuesday", valueLabel: "6", accessibilityLabel: "Tuesday, October 6")], selection: $values.selected).scheduleFrame("days")
                    DateRangeField("Available dates", start: $values.start, end: $values.end, startLabel: "Start date", endLabel: "End date",
                        confirmLabel: "Use date", cancelLabel: "Keep date", error: "End date must be on or after start date.").scheduleFrame("range")
                    TimeField("Session time", selection: $values.time, valueLabel: "09:30", confirmLabel: "Use time", cancelLabel: "Keep time").scheduleFrame("time")
                    AgendaRow("A longer session title", timeLabel: "09:30–10:30", detail: "A personal workspace with longer descriptive copy",
                        status: { Badge("Planned locally") }, actions: { ActionButton("Inspect session", variant: .secondary) {}.scheduleFrame("action") }).scheduleFrame("agenda")
                }.scheduleFrame("content")
            }.frame(width: 240).coordinateSpace(name: "schedule-probe")
        }.environment(\.dynamicTypeSize, values.textSize)
            .onPreferenceChange(ScheduleFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func narrowSchedulingControlsWrapAndGrowWithoutLosingIndependentActionBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 1800)
    let values = ScheduleProbeValues()
    let host = UIHostingController(rootView: ScheduleProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Schedule geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 6 }
    let normal = values.frames
    values.textSize = .accessibility3
    try await settle { (values.frames["content"]?.height ?? 0) > (normal["content"]?.height ?? 0) + 50 }
    let content = try #require(values.frames["content"])
    #expect(content.width <= 241)
    for name in ["days", "range", "time", "agenda", "action"] {
        let frame = try #require(values.frames[name])
        #expect(frame.minX >= content.minX - 1 && frame.maxX <= content.maxX + 1)
    }
    #expect((values.frames["days"]?.height ?? 0) > (normal["days"]?.height ?? 0))
    #expect((values.frames["action"]?.height ?? 0) >= 44)
}
