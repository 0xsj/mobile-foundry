import FoundryUI
import SwiftUI

enum PlanCycle: String, CaseIterable { case monthly = "Monthly", yearly = "Yearly" }
enum PlanTier: String, CaseIterable, Identifiable {
    case starter, studio, team
    var id: String { rawValue }
    var title: String { switch self { case .starter: "Starter"; case .studio: "Studio"; case .team: "Team" } }
    var subtitle: String { switch self { case .starter: "A little room to explore"; case .studio: "More space for everyday work"; case .team: "Shared work for a small team" } }
    var available: Bool { self != .team }
    var quota: Int { switch self { case .starter: 5; case .studio: 50; case .team: 100 } }
    func price(_ cycle: PlanCycle) -> String {
        switch self { case .starter: "$0"; case .studio: cycle == .monthly ? "$8 / month" : "$6 / month"; case .team: cycle == .monthly ? "$18 / month" : "$15 / month" }
    }
    func priceDetail(_ cycle: PlanCycle) -> String {
        if self == .starter { return "No charge" }
        return cycle == .monthly ? "USD · Billed monthly" : "USD · \(self == .studio ? "$72" : "$180") billed yearly"
    }
    func priceNarration(_ cycle: PlanCycle) -> String {
        if self == .starter { return "Starter, free" }
        let amount = self == .studio ? (cycle == .monthly ? 8 : 6) : (cycle == .monthly ? 18 : 15)
        return "\(title), \(amount) USD per month, \(cycle == .monthly ? "billed monthly" : "\(self == .studio ? 72 : 180) USD billed yearly")"
    }
}
/// Local plan choice is a draft, not entitlement. Only an admitted review application changes the current allowance.
struct PlanValues {
    var selected = PlanTier.starter; var cycle = PlanCycle.monthly
    var current = PlanTier.starter; var currentCycle = PlanCycle.monthly; var used = 3
    var enabled = true; var pending = false
    var reviewed: PlanTier?; var reviewedCycle = PlanCycle.monthly; var reviews = 0; var applied = 0
    var canEdit: Bool { enabled && !pending }
    var canReview: Bool { canEdit && selected.available && (selected != current || cycle != currentCycle) }
    var canApplyReviewed: Bool {
        canEdit && reviewed?.available == true && reviewed == selected && reviewedCycle == cycle && (reviewed != current || reviewedCycle != currentCycle)
    }
    var canExport: Bool { canEdit && used < current.quota }
    var usageDetail: String {
        if used < current.quota { let remaining = current.quota - used; return "\(remaining) \(remaining == 1 ? "export" : "exports") remaining" }
        return used == current.quota ? "You've reached the current allowance." : "Current usage exceeds this plan's allowance."
    }
    mutating func select(_ tier: PlanTier) { guard canEdit && tier.available else { return }; selected = tier }
    mutating func chooseCycle(_ value: PlanCycle) { guard canEdit else { return }; cycle = value }
    mutating func review() { guard canReview else { return }; reviewed = selected; reviewedCycle = cycle; reviews += 1 }
    mutating func applyReviewed() { guard canApplyReviewed, let reviewed else { return }; current = reviewed; currentCycle = reviewedCycle; applied += 1 }
    mutating func export() { guard canExport else { return }; used += 1 }
    mutating func reachLimit() { guard canEdit else { return }; used = max(used, current.quota) }
    mutating func resetUsage() { guard canEdit else { return }; used = 0 }
}
struct PlanExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: PlanValues
    var body: some View {
        Card {
            SectionHeader("Plans and allowances", subtitle: "Compare features, choose a plan and project current usage.")
            NavLink("Open plans preview", subtitle: "Try billing choices, review and quota states") {
                PlanPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
            UsageMeter("Workspace members", value: "Unlimited", detail: "An unmetered allowance can omit the bar.", accessibilityLabel: "Workspace members, unlimited")
        }
        PlanContent(values: $values)
    }
}
struct PlanPreview: View {
    @Binding var values: PlanValues
    let appearance: FoundryAppearance; let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { PlanContent(values: $values).padding(20) } }
            .navigationTitle("Plans preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct PlanContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: PlanValues
    @State private var showReview = false
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable plan actions", isOn: $values.enabled)
                ToggleField("Show pending plan change", isOn: $values.pending, enabled: values.enabled)
                SelectField("Billing choice", selection: Binding(get: { values.cycle }, set: { values.chooseCycle($0) }), options: PlanCycle.allCases,
                            enabled: values.canEdit, label: { $0.rawValue })
                Text("Local plan preview. Applying a choice changes this preview's allowance.").font(t.typography.caption)
                KeyValueRow("Current plan", value: "\(values.current.title) · \(values.currentCycle.rawValue)")
                KeyValueRow("Selected plan", value: "\(values.selected.title) · \(values.cycle.rawValue)")
                ActionButton("Review plan change", isBusy: values.pending, enabled: values.canReview) {
                    values.review(); showReview = values.canApplyReviewed
                }
                Text("Plan reviews: \(values.reviews) · Changes applied: \(values.applied)").font(t.typography.caption)
                Text("Last reviewed: \(values.reviewed.map { "\($0.title) · \(values.reviewedCycle.rawValue)" } ?? "None")").font(t.typography.caption)
            }
            Card {
                UsageMeter("Monthly exports", value: "\(values.used) of \(values.current.quota)", detail: values.usageDetail,
                           fraction: Double(values.used) / Double(values.current.quota),
                           accessibilityLabel: "Monthly exports, \(values.used) of \(values.current.quota), \(values.usageDetail)")
                ActionButton("Simulate export", variant: .secondary, enabled: values.canExport) { values.export() }
                ActionButton("Reach current limit", variant: .quiet, enabled: values.canEdit) { values.reachLimit() }
                ActionButton("Reset usage", variant: .quiet, enabled: values.canEdit) { values.resetUsage() }
            }
            ForEach(PlanTier.allCases) { tier in
                PlanCard(tier.title, subtitle: tier.subtitle, selected: values.selected == tier,
                         actionLabel: tier.available ? "\(values.selected == tier ? "Selected" : "Choose") \(tier.title)" : "\(tier.title) unavailable",
                         enabled: values.canEdit && tier.available, onSelect: { values.select(tier) }, price: {
                    PriceLabel(tier.price(values.cycle), detail: tier.priceDetail(values.cycle), accessibilityLabel: tier.priceNarration(values.cycle))
                }, status: { Badge(tier == values.current ? "Current plan" : tier == .team ? "Coming soon" : tier == .studio ? "More room" : "Essentials", tone: tier.available ? .neutral : .warning) }, features: {
                    FeatureRow("Monthly exports", detail: "\(tier.quota) each month", stateLabel: "Included", included: true,
                               accessibilityLabel: "\(tier.title), \(tier.quota) monthly exports included")
                    FeatureRow("Offline drafts", stateLabel: "Included", included: true, accessibilityLabel: "\(tier.title), offline drafts included")
                    FeatureRow("Shared workspaces", stateLabel: tier == .team ? "Included" : "Not included", included: tier == .team,
                               accessibilityLabel: "\(tier.title), shared workspaces \(tier == .team ? "included" : "not included")")
                })
            }
        }
        .sheetPanel("Plan review", isPresented: $showReview, closeLabel: "Close plan review") {
            ScrollView {
                if let tier = values.reviewed {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        SectionHeader("Review \(tier.title)", subtitle: values.reviewedCycle.rawValue)
                        PriceLabel(tier.price(values.reviewedCycle), detail: tier.priceDetail(values.reviewedCycle), accessibilityLabel: tier.priceNarration(values.reviewedCycle))
                        Text("Usage stays at \(values.used). The preview allowance becomes \(tier.quota) exports per month.").font(t.typography.body)
                        ActionButton("Apply preview change", enabled: values.canApplyReviewed) { values.applyReviewed() }
                    }
                }
            }.presentationDetents([.medium, .large])
        }
        .onChange(of: values.canApplyReviewed) { _, available in if !available { showReview = false } }
    }
}
