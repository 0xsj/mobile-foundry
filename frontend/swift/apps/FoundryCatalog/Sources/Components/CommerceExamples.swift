import FoundryUI
import SwiftUI

enum CartDelivery: String, CaseIterable { case ship = "Ship", pickup = "Pick up" }
struct CartProduct: Identifiable {
    let id: String; let title: String; let detail: String; let cents: Int; let previous: Int?; let limit: Int; let symbol: String
    static let all = [Self(id: "kit", title: "Studio kit", detail: "A small kit for your next idea", cents: 2900, previous: 3900, limit: 3, symbol: "shippingbox"),
                      Self(id: "notebook", title: "Pocket notebook", detail: "Keep sketches and field notes nearby", cents: 1200, previous: nil, limit: 5, symbol: "book.closed"),
                      Self(id: "case", title: "Travel case", detail: "Carry a few useful essentials", cents: 1800, previous: nil, limit: 0, symbol: "bag")]
}
/// Fixed USD copy for bounded local integer-cent fixtures; production formatting belongs to the domain boundary.
func cartMoney(_ cents: Int) -> String {
    precondition(cents >= 0)
    let fraction = cents % 100
    return "$\(cents / 100).\(fraction < 10 ? "0" : "")\(fraction)"
}
struct CommerceValues {
    var kit = 1; var notebook = 0
    var delivery = CartDelivery.ship
    var code = ""; var discounted = false; var error: String?
    var enabled = true; var codeBusy = false
    var reviews = 0; var reviewedTotal: Int?; var reviewedItems: Int?
    var itemCount: Int { kit + notebook }
    var subtotal: Int { kit * 2900 + notebook * 1200 }
    var discount: Int { discounted ? subtotal / 10 : 0 }
    var shipping: Int { itemCount == 0 || delivery == .pickup ? 0 : 500 }
    var total: Int { subtotal - discount + shipping }
    var normalizedCode: String { code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
    var canApply: Bool { enabled && !codeBusy && itemCount > 0 && !normalizedCode.isEmpty && (!discounted || normalizedCode != "STUDIO10") }
    var canReview: Bool { enabled && !codeBusy && itemCount > 0 }
    func quantity(_ id: String) -> Int { id == "kit" ? kit : id == "notebook" ? notebook : 0 }
    mutating func setQuantity(_ id: String, _ value: Int) {
        guard enabled, let product = CartProduct.all.first(where: { $0.id == id }), product.limit > 0, (0...product.limit).contains(value) else { return }
        if id == "kit" { kit = value } else if id == "notebook" { notebook = value }
    }
    mutating func chooseDelivery(_ value: CartDelivery) { guard enabled else { return }; delivery = value }
    mutating func setCode(_ value: String) { guard enabled && !codeBusy else { return }; code = value; error = nil }
    mutating func applyCode() {
        guard canApply else { return }
        if normalizedCode == "STUDIO10" { discounted = true; error = nil }
        else { error = "That code isn't available in this preview." }
    }
    mutating func removeDiscount() { guard enabled && !codeBusy else { return }; discounted = false; error = nil }
    mutating func clearCart() { guard enabled else { return }; kit = 0; notebook = 0 }
    mutating func restoreCart() { guard enabled else { return }; kit = 1; notebook = 0 }
    mutating func review() { guard canReview else { return }; reviewedTotal = total; reviewedItems = itemCount; reviews += 1 }
}
struct CommerceExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: CommerceValues
    var body: some View {
        Card {
            SectionHeader("Products and totals", subtitle: "Readable prices, adjustable quantities and explicit code application.")
            NavLink("Open cart preview", subtitle: "Try quantities, delivery and discounts in a local cart") {
                CommercePreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        CommerceContent(values: $values)
    }
}
struct CommercePreview: View {
    @Binding var values: CommerceValues
    let appearance: FoundryAppearance; let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { CommerceContent(values: $values).padding(20) }.scrollDismissesKeyboard(.interactively) }
            .navigationTitle("Cart preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct CommerceContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: CommerceValues
    @FocusState private var codeFocused: Bool
    @State private var showReview = false
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable cart actions", isOn: $values.enabled)
                ToggleField("Show busy code field", isOn: $values.codeBusy, enabled: values.enabled)
                Text("Local cart preview. Review records a snapshot of these amounts.").font(t.typography.caption)
            }
            ForEach(CartProduct.all) { product in
                Card {
                    ProductRow(product.title, detail: product.detail, artwork: {
                        Image(systemName: product.symbol).font(.title2).frame(width: 56, height: 56)
                            .background(t.colors.accentTint.color, in: RoundedRectangle(cornerRadius: t.shape.radii[2]))
                    }, price: {
                        PriceLabel(cartMoney(product.cents), comparison: product.previous.map(cartMoney), detail: "USD · Each",
                                   accessibilityLabel: "\(product.title), \(cartMoney(product.cents)) USD each\(product.previous.map { ", previously \(cartMoney($0)) USD" } ?? "")")
                    }, status: { Badge(product.limit == 0 ? "Unavailable" : "Up to \(product.limit) per cart", tone: product.limit == 0 ? .warning : .neutral) }, actions: {
                        ValueStepper("Quantity for \(product.title)", value: Binding(get: { values.quantity(product.id) }, set: { values.setQuantity(product.id, $0) }),
                                     valueLabel: "\(values.quantity(product.id)) in cart", decreaseLabel: "Decrease \(product.title)", increaseLabel: "Increase \(product.title)",
                                     range: 0...product.limit, enabled: values.enabled && product.limit > 0)
                    })
                }
            }
            Card(.floating) {
                SelectField("Delivery method", selection: Binding(get: { values.delivery }, set: { values.chooseDelivery($0) }),
                            options: CartDelivery.allCases, enabled: values.enabled, label: { $0.rawValue })
                InlineActionField("Promo code", text: Binding(get: { values.code }, set: { values.setCode($0) }),
                                  actionLabel: values.codeBusy ? "Applying code…" : "Apply code", canSubmit: values.canApply,
                                  enabled: values.enabled, isBusy: values.codeBusy, help: "Use STUDIO10 for 10% off items.", error: values.error,
                                  focus: $codeFocused, onSubmit: { codeFocused = false; values.applyCode() })
                    .textInputAutocapitalization(.characters).autocorrectionDisabled()
                if values.discounted {
                    RemovableChip("STUDIO10 · 10%", removeLabel: "Remove discount", enabled: values.enabled && !values.codeBusy) { values.removeDiscount() }
                }
                ActionButton("Clear cart", variant: .quiet, enabled: values.enabled && values.itemCount > 0) { values.clearCart() }
                ActionButton("Restore cart", variant: .quiet, enabled: values.enabled) { values.restoreCart() }
            }
            if values.itemCount == 0 { Card { EmptyState("Your cart is empty", message: "Add an available item or restore the preview cart.") } }
            OrderSummary("Order summary", subtitle: "\(values.itemCount) items", totalTitle: "Total", totalValue: cartMoney(values.total), totalDetail: "USD · Local preview", lines: {
                KeyValueRow("Items subtotal", value: cartMoney(values.subtotal))
                KeyValueRow("Discount", value: values.discount == 0 ? cartMoney(0) : "−\(cartMoney(values.discount))")
                KeyValueRow("Delivery", value: cartMoney(values.shipping))
            }, footer: {
                ActionButton("Preview review", enabled: values.canReview) { codeFocused = false; values.review(); showReview = true }
                Text("Reviews opened: \(values.reviews)").font(t.typography.caption)
                Text("Last reviewed total: \(values.reviewedTotal.map(cartMoney) ?? "None")").font(t.typography.caption)
            })
        }
        .sheetPanel("Cart review", isPresented: $showReview, closeLabel: "Close review") {
            ScrollView {
                VStack(alignment: .leading, spacing: t.space.section) {
                    Text("Local review snapshot").font(t.typography.label)
                    KeyValueRow("Items reviewed", value: "\(values.reviewedItems ?? 0)")
                    PriceLabel(values.reviewedTotal.map(cartMoney) ?? cartMoney(0), detail: "USD", accessibilityLabel: "Reviewed total: \(values.reviewedTotal.map(cartMoney) ?? cartMoney(0)) USD")
                }
            }.presentationDetents([.medium, .large])
        }
        .onChange(of: values.canReview) { _, canReview in if !canReview { showReview = false } }
    }
}
