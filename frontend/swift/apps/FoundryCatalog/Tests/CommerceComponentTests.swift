@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func cartTotalsFollowCommittedChoicesAndReviewRetainsItsSnapshot() {
    var values = CommerceValues()
    #expect(values.total == 3400 && values.itemCount == 1)
    values.setQuantity("case", 1); values.setQuantity("kit", 4); values.setQuantity("notebook", -1); values.setQuantity("missing", 1)
    #expect(values.kit == 1 && values.notebook == 0)
    values.setQuantity("kit", 3); values.setQuantity("notebook", 1); values.chooseDelivery(.pickup)
    #expect(values.subtotal == 9900 && values.shipping == 0)
    values.setCode("bad"); values.applyCode()
    #expect(values.error != nil && !values.discounted && values.total == 9900)
    values.setCode(" studio10 "); #expect(values.discount == 0)
    values.applyCode(); values.review()
    #expect(values.error == nil && values.total == 8910 && values.reviewedTotal == 8910 && values.reviewedItems == 4)
    values.setCode("bad"); values.applyCode()
    #expect(values.discounted && values.total == 8910 && values.error != nil)
    values.codeBusy = true; values.setCode("STUDIO10"); values.removeDiscount(); values.applyCode(); values.review()
    #expect(values.code == "bad" && values.discounted && values.reviews == 1)
    values.codeBusy = false; values.enabled = false
    values.clearCart(); values.restoreCart(); values.chooseDelivery(.ship); values.setQuantity("kit", 1); values.setCode(""); values.review()
    #expect(values.kit == 3 && values.notebook == 1 && values.delivery == .pickup && values.code == "bad" && values.reviews == 1)
    values.enabled = true; values.clearCart(); values.review()
    #expect(values.total == 0 && values.discounted && values.reviewedTotal == 8910 && values.reviews == 1)
    values.restoreCart(); #expect(values.total == 2610 && values.itemCount == 1)
    values.removeDiscount(); #expect(values.total == 2900 && values.reviewedTotal == 8910)
    #expect(cartMoney(0) == "$0.00" && cartMoney(1) == "$0.01" && cartMoney(99) == "$0.99" && cartMoney(105) == "$1.05")
}
@Observable @MainActor private final class CommerceProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
    var draft = "STUDIO10"
}
private struct CommerceFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func commerceFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: CommerceFrames.self, value: [id: proxy.frame(in: .named("commerce-probe"))]) } }
    }
}
private struct CommerceProbe: View {
    @Bindable var values: CommerceProbeValues
    @FocusState private var focused: Bool
    var body: some View {
        FoundryTheme {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ProductRow("A complete studio kit for new ideas", detail: "Product copy wraps alongside supplied artwork", artwork: {
                        Image(systemName: "shippingbox").frame(width: 48, height: 48).commerceFrame("artwork")
                    }, price: { PriceLabel("€1.234,50", comparison: "€1.599,00", detail: "Caller-formatted price", accessibilityLabel: "Supplied current and previous prices").commerceFrame("price") },
                               status: { Badge("Available") }, actions: { ActionButton("Add studio kit") {}.commerceFrame("add") }).commerceFrame("product")
                    InlineActionField("An invite or promotional code", text: $values.draft, actionLabel: "Apply the code", canSubmit: true,
                                      help: "The caller decides how this code is validated.", focus: $focused, onSubmit: {}).commerceFrame("field")
                    OrderSummary("Order summary", totalTitle: "Total", totalValue: "€1.234,50", totalDetail: "Supplied amount", lines: {
                        KeyValueRow("Items", value: "€1.234,50")
                    }, footer: { ActionButton("Review order") {}.commerceFrame("review") }).commerceFrame("summary")
                }
            }.frame(width: 240).coordinateSpace(name: "commerce-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(CommerceFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func commerceCompositionsGrowWithoutCompressingActionsOrChangingDrafts() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 2400)
    let values = CommerceProbeValues()
    let host = UIHostingController(rootView: CommerceProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Commerce geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 7 }
    let normal = try #require(values.frames["product"]), normalField = try #require(values.frames["field"])
    values.textSize = .accessibility3
    try await settle { (values.frames["product"]?.height ?? 0) > normal.height + 40 && (values.frames["field"]?.height ?? 0) > normalField.height + 40 }
    for (regionID, actionID) in [("product", "add"), ("summary", "review")] {
        let region = try #require(values.frames[regionID]), action = try #require(values.frames[actionID])
        #expect(region.width <= 241 && action.height >= 44 && action.width >= 44)
        #expect(action.minX >= region.minX - 1 && action.maxX <= region.maxX + 1)
    }
    let artwork = try #require(values.frames["artwork"])
    values.direction = .rightToLeft
    try await settle { (values.frames["artwork"]?.minX ?? 0) > artwork.minX + 100 }
    #expect(values.draft == "STUDIO10")
}
