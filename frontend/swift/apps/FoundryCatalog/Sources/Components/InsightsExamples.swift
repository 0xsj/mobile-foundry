import FoundryUI
import SwiftUI

enum InsightPeriod: String, CaseIterable { case week = "Week", month = "Month" }
struct InsightsValues {
    var period: InsightPeriod = .week
    var completed = 14
    var enabled = true
    var empty = false
    var samples: [Double] { empty ? [] : period == .week ? [18, 24, 21, 32, 30, 41, 44] : [92, 108, 96, 124] }
    var total: Int { Int(samples.reduce(0, +)) }
    var comparison: String { period == .week ? "Up 30 min versus previous week" : "Down 40 min versus previous month" }
    var summary: String { samples.isEmpty ? "No focus samples for this period" : "\(period.rawValue) focus: \(total) minutes across \(samples.count) samples. First \(Int(samples.first!)) minutes; last \(Int(samples.last!)) minutes." }
    var bars: [ChartBar] {
        guard !empty else { return [] }
        let factor = period == .week ? 1 : 2
        return zip(["Design", "Reading", "Practice"], [84, 70, 56]).map { label, base in
            ChartBar(id: label, label: label, value: Double(base * factor), valueLabel: "\(base * factor) min")
        }
    }
    mutating func setCompleted(_ value: Int) { guard enabled, (0...20).contains(value) else { return }; completed = value }
    mutating func resetGoal() { guard enabled else { return }; completed = 14 }
}
struct InsightsExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: InsightsValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card {
                Text("Insights and goals").font(t.typography.heading)
                Text("Small charts, readable values and your own dashboard controls.")
                NavLink("Open insights dashboard", subtitle: "Trends, category bars and a session goal") {
                    InsightsPreview(values: $values, appearance: t.appearance, style: t.materials.style)
                }
            }
            InsightsContent(values: $values)
        }
    }
}
struct InsightsPreview: View {
    @Binding var values: InsightsValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            ScrollView { InsightsContent(values: $values).padding(20) }
        }.navigationTitle("Insights dashboard").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
private struct InsightsContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: InsightsValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                Tabs("Insight period", selection: $values.period, options: InsightPeriod.allCases, label: { $0.rawValue })
                ToggleField("Show empty insight data", isOn: $values.empty)
                ToggleField("Enable goal controls", isOn: $values.enabled)
            }
            ChartPanel("Focus activity", subtitle: "Relative shape; equally spaced samples, no shared vertical scale.", plot: {
                if values.empty {
                    EmptyState("No focus activity", message: "Choose a period with samples to compare activity.")
                } else {
                    Text("\(values.total) min").font(t.typography.title).monospacedDigit()
                    Sparkline(values.samples, summary: values.summary, height: 96)
                }
            }, legend: {
                LegendItem(values.period == .week ? "Daily focus minutes" : "Weekly focus minutes", mark: .line)
            }, footer: {
                if !values.empty {
                    TrendBadge(values.comparison, direction: values.period == .week ? .up : .down, tone: .info)
                    DisclosureSection("Show focus values", isExpanded: $showValues, stateDescription: showValues ? "Values shown" : "Values hidden") {
                        Text(values.samples.map { "\(Int($0))" }.joined(separator: ", ") + " minutes")
                    }
                }
            })
            ChartPanel("Time by category", subtitle: "Scale: 0–200 minutes. Same scale for both periods.", plot: {
                if values.empty { Text("No category values for this period.") }
                else { BarChart("Focus minutes by category", bars: values.bars, maximum: 200) }
            }, legend: { LegendItem("Focus minutes", mark: .square) }, footer: { EmptyView() })
            ChartPanel("Session goal", subtitle: "An independent local target; changing period keeps this value.", role: .floating, plot: {
                ProgressRing("Completed sessions", fraction: Double(values.completed) / 20, valueLabel: "\(values.completed) of 20")
                    .frame(maxWidth: .infinity)
            }, legend: { LegendItem("Completed", mark: .dot); LegendItem("Remaining", color: t.colors.line.color, mark: .square) }, footer: {
                ValueStepper("Completed session count", value: Binding(get: { values.completed }, set: { values.setCompleted($0) }),
                    valueLabel: "\(values.completed) sessions", decreaseLabel: "Decrease completed sessions", increaseLabel: "Increase completed sessions",
                    range: 0...20, enabled: values.enabled)
                ActionButton("Reset session goal", variant: .quiet, enabled: values.enabled) { values.resetGoal() }
                Text("Local preview data. No analytics source or activity tracking.").font(t.typography.caption)
            })
        }
    }
    @State private var showValues = false
}
