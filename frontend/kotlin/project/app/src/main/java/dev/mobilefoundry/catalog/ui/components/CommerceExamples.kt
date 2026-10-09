package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.display.pricelabel.PriceLabel
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.inlineaction.InlineActionField
import dev.mobilefoundry.ui.components.forms.removablechip.RemovableChip
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.stepper.ValueStepper
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.patterns.ordersummary.OrderSummary
import dev.mobilefoundry.ui.components.patterns.productrow.ProductRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class CartDelivery(val label: String) { SHIP("Ship"), PICKUP("Pick up") }
internal data class CartProduct(val id: String, val title: String, val detail: String, val cents: Int, val previous: Int?, val limit: Int, val artwork: String) {
    companion object { val all = listOf(CartProduct("kit", "Studio kit", "A small kit for your next idea", 2900, 3900, 3, "KIT"),
        CartProduct("notebook", "Pocket notebook", "Keep sketches and field notes nearby", 1200, null, 5, "NOTE"),
        CartProduct("case", "Travel case", "Carry a few useful essentials", 1800, null, 0, "CASE")) }
}
/** Fixed USD copy for bounded integer-cent fixtures, not production monetary formatting. */
internal fun cartMoney(cents: Int): String {
    require(cents >= 0)
    return "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
internal data class CommerceValues(val kit: Int = 1, val notebook: Int = 0, val delivery: CartDelivery = CartDelivery.SHIP,
    val code: String = "", val discounted: Boolean = false, val error: String? = null, val enabled: Boolean = true, val codeBusy: Boolean = false,
    val reviews: Int = 0, val reviewedTotal: Int? = null, val reviewedItems: Int? = null) {
    val itemCount get() = kit + notebook
    val subtotal get() = kit * 2900 + notebook * 1200
    val discount get() = if (discounted) subtotal / 10 else 0
    val shipping get() = if (itemCount == 0 || delivery == CartDelivery.PICKUP) 0 else 500
    val total get() = subtotal - discount + shipping
    val normalizedCode get() = code.trim().uppercase(java.util.Locale.ROOT)
    val canApply get() = enabled && !codeBusy && itemCount > 0 && normalizedCode.isNotEmpty() && (!discounted || normalizedCode != "STUDIO10")
    val canReview get() = enabled && !codeBusy && itemCount > 0
    fun quantity(id: String) = when (id) { "kit" -> kit; "notebook" -> notebook; else -> 0 }
    fun setQuantity(id: String, value: Int): CommerceValues {
        val product = CartProduct.all.firstOrNull { it.id == id } ?: return this
        if (!enabled || product.limit == 0 || value !in 0..product.limit) return this
        return when (id) { "kit" -> copy(kit = value); "notebook" -> copy(notebook = value); else -> this }
    }
    fun chooseDelivery(value: CartDelivery) = if (enabled) copy(delivery = value) else this
    fun setCode(value: String) = if (enabled && !codeBusy) copy(code = value, error = null) else this
    fun applyCode() = if (!canApply) this else if (normalizedCode == "STUDIO10") copy(discounted = true, error = null)
        else copy(error = "That code isn't available in this preview.")
    fun removeDiscount() = if (enabled && !codeBusy) copy(discounted = false, error = null) else this
    fun clearCart() = if (enabled) copy(kit = 0, notebook = 0) else this
    fun restoreCart() = if (enabled) copy(kit = 1, notebook = 0) else this
    fun review() = if (canReview) copy(reviewedTotal = total, reviewedItems = itemCount, reviews = reviews + 1) else this
}
@Composable
internal fun CommerceExamples(values: CommerceValues, onChange: (CommerceValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Products and totals", subtitle = "Readable prices, adjustable quantities and explicit code application.")
        NavLink("Open cart preview", onPreview, subtitle = "Try quantities, delivery and discounts in a local cart")
    }
    CommerceContent(values, onChange)
}
@Composable
internal fun CommercePreviewScreen(values: CommerceValues, onChange: (CommerceValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Cart preview", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("commerce-scroll").padding(20.dp)) { CommerceContent(values, onChange) }
    }
}
@Composable
internal fun CommerceContent(values: CommerceValues, onChange: (CommerceValues) -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    var showReview by remember(values.canReview) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable cart actions", values.enabled, { onChange(values.copy(enabled = it)) })
            ToggleField("Show busy code field", values.codeBusy, { onChange(values.copy(codeBusy = it)) }, enabled = values.enabled)
            Text("Local cart preview. Review records a snapshot of these amounts.", style = t.typography.caption)
        }
        CartProduct.all.forEach { product -> key(product.id) { Card {
            ProductRow(product.title, detail = product.detail, artwork = {
                Box(Modifier.size(56.dp).background(t.colors.accentTint.color, RoundedCornerShape(t.shape.radii[2])), contentAlignment = Alignment.Center) {
                    Text(product.artwork, style = t.typography.caption, color = t.colors.ink.color)
                }
            }, price = { PriceLabel(cartMoney(product.cents), "${product.title}, ${cartMoney(product.cents)} USD each" +
                (product.previous?.let { ", previously ${cartMoney(it)} USD" } ?: ""), comparison = product.previous?.let(::cartMoney), detail = "USD · Each") },
                status = { Badge(if (product.limit == 0) "Unavailable" else "Up to ${product.limit} per cart", tone = if (product.limit == 0) MessageTone.WARNING else MessageTone.NEUTRAL) },
                actions = { ValueStepper("Quantity for ${product.title}", values.quantity(product.id), { onChange(values.setQuantity(product.id, it)) },
                    "${values.quantity(product.id)} in cart", "Decrease ${product.title}", "Increase ${product.title}",
                    range = 0..product.limit, enabled = values.enabled && product.limit > 0) })
        } } }
        Card(role = SurfaceRole.FLOATING) {
            SelectField("Delivery method", CartDelivery.entries, values.delivery, { onChange(values.chooseDelivery(it)) }, enabled = values.enabled, label = { it.label })
            InlineActionField("Promo code", values.code, { onChange(values.setCode(it)) }, if (values.codeBusy) "Applying code…" else "Apply code", values.canApply,
                { focus.clearFocus(); onChange(values.applyCode()) }, enabled = values.enabled, isBusy = values.codeBusy,
                help = "Use STUDIO10 for 10% off items.", error = values.error, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            if (values.discounted) RemovableChip("STUDIO10 · 10%", "Remove discount", { onChange(values.removeDiscount()) }, enabled = values.enabled && !values.codeBusy)
            ActionButton({ onChange(values.clearCart()) }, variant = ButtonVariant.QUIET, enabled = values.enabled && values.itemCount > 0) { Text("Clear cart") }
            ActionButton({ onChange(values.restoreCart()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Restore cart") }
        }
        if (values.itemCount == 0) Card { EmptyState("Your cart is empty", "Add an available item or restore the preview cart.") }
        OrderSummary("Order summary", "Total", cartMoney(values.total), subtitle = "${values.itemCount} items", totalDetail = "USD · Local preview", lines = {
            KeyValueRow("Items subtotal", cartMoney(values.subtotal))
            KeyValueRow("Discount", if (values.discount == 0) cartMoney(0) else "−${cartMoney(values.discount)}")
            KeyValueRow("Delivery", cartMoney(values.shipping))
        }, footer = {
            ActionButton({ focus.clearFocus(); onChange(values.review()); showReview = true }, enabled = values.canReview) { Text("Preview review") }
            Text("Reviews opened: ${values.reviews}", style = t.typography.caption)
            Text("Last reviewed total: ${values.reviewedTotal?.let(::cartMoney) ?: "None"}", style = t.typography.caption)
        })
    }
    SheetPanel("Cart review", showReview, { showReview = false }, "Close review") {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            Text("Local review snapshot", style = t.typography.label)
            KeyValueRow("Items reviewed", "${values.reviewedItems ?: 0}")
            PriceLabel(values.reviewedTotal?.let(::cartMoney) ?: cartMoney(0), "Reviewed total: ${values.reviewedTotal?.let(::cartMoney) ?: cartMoney(0)} USD", detail = "USD")
        }
    }
}
