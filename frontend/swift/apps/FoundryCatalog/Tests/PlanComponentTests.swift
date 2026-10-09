@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func planDraftsAndReviewedChangesDoNotSilentlyRewriteCurrentUsage() {
    var values = PlanValues()
    #expect(!values.canReview && values.current.quota == 5 && values.used == 3)
    values.export(); #expect(values.usageDetail == "1 export remaining"); values.used = 3
    values.select(.team); values.review(); values.applyReviewed()
    #expect(values.selected == .starter && values.reviewed == nil && values.applied == 0)
    values.select(.studio); values.chooseCycle(.yearly); values.review()
    #expect(values.current == .starter && values.used == 3 && values.canApplyReviewed && values.reviewedCycle == .yearly)
    values.chooseCycle(.monthly); values.applyReviewed()
    #expect(values.current == .starter && values.applied == 0 && values.reviewedCycle == .yearly)
    values.review(); values.pending = true
    values.select(.starter); values.chooseCycle(.yearly); values.export(); values.resetUsage(); values.reachLimit(); values.applyReviewed(); values.review()
    #expect(values.selected == .studio && values.cycle == .monthly && values.current == .starter && values.used == 3 && values.reviews == 2)
    values.pending = false; values.applyReviewed()
    #expect(values.current == .studio && values.currentCycle == .monthly && values.used == 3 && values.applied == 1 && !values.canApplyReviewed)
    values.applyReviewed(); #expect(values.applied == 1)
    values.reachLimit(); values.export(); #expect(values.used == 50 && !values.canExport)
    values.select(.starter); values.review(); values.applyReviewed()
    #expect(values.current == .starter && values.used == 50 && !values.canExport && values.usageDetail.contains("exceeds"))
    values.reachLimit(); #expect(values.used == 50)
    values.enabled = false
    values.resetUsage(); values.export(); values.select(.studio); values.chooseCycle(.yearly); values.review(); values.applyReviewed()
    #expect(values.used == 50 && values.selected == .starter && values.current == .starter && values.applied == 2 && values.reviews == 3)
    values.enabled = true; values.resetUsage(); #expect(values.used == 0 && values.canExport)
    for _ in 0..<6 { values.export() }
    #expect(values.used == 5 && values.usageDetail.contains("reached"))
    #expect(PlanTier.studio.priceDetail(.yearly).contains("$72") && PlanTier.studio.priceNarration(.yearly).contains("72 USD billed yearly"))
}
@Observable @MainActor private final class PlanProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct PlanFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func planFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: PlanFrames.self, value: [id: proxy.frame(in: .named("plans-probe"))]) } }
    }
}
private struct PlanProbe: View {
    @Bindable var values: PlanProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    PlanCard("A flexible plan for your next project", subtitle: "Compare supplied benefits and billing meaning", selected: false,
                             actionLabel: "Choose this plan", onSelect: {}, price: {
                        PriceLabel("€6 / month", detail: "€72 billed yearly", accessibilityLabel: "Supplied yearly price").planFrame("price")
                    }, status: {
                        HStack { Image(systemName: "sparkles").frame(width: 24, height: 24).planFrame("mark"); Spacer() }.accessibilityHidden(true)
                        ActionButton("Read plan details", variant: .quiet) {}.planFrame("details")
                    }, features: {
                        FeatureRow("Shared spaces for a growing team", detail: "Supplied feature detail can wrap", stateLabel: "Not included", included: false,
                                   accessibilityLabel: "Shared spaces, not included").planFrame("feature")
                    }).planFrame("plan")
                    UsageMeter("Monthly exports", value: "8 of 5", detail: "Current usage exceeds the allowance", fraction: 1.6,
                               accessibilityLabel: "Monthly exports, 8 of 5, exceeded").planFrame("usage")
                    UsageMeter("Workspace members", value: "Unlimited", accessibilityLabel: "Workspace members, unlimited").planFrame("unmetered")
                }
            }.frame(width: 240).coordinateSpace(name: "plans-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(PlanFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func planAndUsageCopyGrowWithinNarrowBoundsAndKeepSlotActionsNative() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 3000)
    let values = PlanProbeValues()
    let host = UIHostingController(rootView: PlanProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Plans geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 7 }
    let normal = try #require(values.frames["plan"]), normalUsage = try #require(values.frames["usage"])
    values.textSize = .accessibility3
    try await settle { (values.frames["plan"]?.height ?? 0) > normal.height + 80 && (values.frames["usage"]?.height ?? 0) > normalUsage.height + 40 }
    let plan = try #require(values.frames["plan"]), action = try #require(values.frames["details"])
    #expect(action.width >= 44 && action.height >= 44 && action.minX >= plan.minX - 1 && action.maxX <= plan.maxX + 1)
    for id in ["plan", "price", "feature", "usage", "unmetered"] { #expect((values.frames[id]?.width ?? 1000) <= 241) }
    let mark = try #require(values.frames["mark"])
    values.direction = .rightToLeft
    try await settle { (values.frames["mark"]?.minX ?? 0) > mark.minX + 100 }
}
