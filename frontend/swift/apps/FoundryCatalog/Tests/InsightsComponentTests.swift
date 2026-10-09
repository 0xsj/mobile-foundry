@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func insightsPeriodEmptyProjectionAndGoalAdmissionStayIndependent() {
    var values = InsightsValues()
    #expect(values.total == 210 && values.samples.count == 7)
    #expect(values.bars.reduce(0) { $0 + Int($1.value) } == values.total)
    values.setCompleted(20); values.period = .month
    #expect(values.total == 420 && values.completed == 20)
    #expect(values.bars.reduce(0) { $0 + Int($1.value) } == values.total)
    #expect(values.comparison == "Down 40 min versus previous month")
    values.empty = true
    #expect(values.samples.isEmpty && values.bars.isEmpty && values.completed == 20)
    values.enabled = false; values.setCompleted(3); values.resetGoal()
    #expect(values.completed == 20)
    values.enabled = true; values.setCompleted(-1); values.setCompleted(21)
    #expect(values.completed == 20)
    values.resetGoal(); values.empty = false
    #expect(values.completed == 14 && values.total == 420)
}
@Observable @MainActor private final class InsightProbeValues {
    var textSize: DynamicTypeSize = .large
    var frames: [String: CGRect] = [:]
}
private struct InsightProbeFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func insightFrame(_ name: String) -> some View {
        background { GeometryReader { geometry in Color.clear.preference(key: InsightProbeFrames.self, value: [name: geometry.frame(in: .named("insight-probe"))]) } }
    }
}
private struct InsightProbe: View {
    @Bindable var values: InsightProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                ChartPanel("A dashboard with longer chart copy", plot: {
                    BarChart("Focus by category", bars: [ChartBar(id: "one", label: "A longer category label", value: 75, valueLabel: "75 minutes")], maximum: 100)
                        .insightFrame("bars")
                    ProgressRing("Completed sessions", fraction: 0.7, valueLabel: "14 of 20").insightFrame("ring")
                    Sparkline([18, 24, 21], summary: "Three focus samples", height: 64).insightFrame("spark")
                }, legend: { LegendItem("A longer legend with meaningful units", mark: .line) }, footer: {
                    ActionButton("Inspect sample values", variant: .secondary) {}.insightFrame("action")
                }).insightFrame("panel")
            }.frame(width: 240).coordinateSpace(name: "insight-probe")
        }.environment(\.dynamicTypeSize, values.textSize)
            .onPreferenceChange(InsightProbeFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func narrowInsightPanelsGrowAtLargerTextWithoutLosingPlotAndActionBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 1400)
    let values = InsightProbeValues()
    let host = UIHostingController(rootView: InsightProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Insight geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 5 }
    let normal = values.frames
    values.textSize = .accessibility3
    try await settle { (values.frames["ring"]?.height ?? 0) > (normal["ring"]?.height ?? 0) + 20 }
    let panel = try #require(values.frames["panel"])
    #expect(panel.width <= 241 && panel.height > (normal["panel"]?.height ?? 0))
    for name in ["bars", "ring", "spark", "action"] {
        let frame = try #require(values.frames[name])
        #expect(frame.minX >= panel.minX - 1 && frame.maxX <= panel.maxX + 1)
    }
    #expect(abs((values.frames["spark"]?.height ?? 0) - 64) < 1)
    #expect((values.frames["action"]?.height ?? 0) >= 44)
}
