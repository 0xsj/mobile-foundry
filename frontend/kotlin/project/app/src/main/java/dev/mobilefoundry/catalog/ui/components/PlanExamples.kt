package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.featurerow.FeatureRow
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.display.pricelabel.PriceLabel
import dev.mobilefoundry.ui.components.display.usagemeter.UsageMeter
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.patterns.plancard.PlanCard
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class PlanCycle(val label: String) { MONTHLY("Monthly"), YEARLY("Yearly") }
internal enum class PlanTier(val title: String, val subtitle: String, val quota: Int, val available: Boolean) {
    STARTER("Starter", "A little room to explore", 5, true), STUDIO("Studio", "More space for everyday work", 50, true), TEAM("Team", "Shared work for a small team", 100, false);
    fun price(cycle: PlanCycle) = when (this) {
        STARTER -> "$0"; STUDIO -> if (cycle == PlanCycle.MONTHLY) "$8 / month" else "$6 / month"; TEAM -> if (cycle == PlanCycle.MONTHLY) "$18 / month" else "$15 / month"
    }
    fun priceDetail(cycle: PlanCycle) = if (this == STARTER) "No charge" else if (cycle == PlanCycle.MONTHLY) "USD · Billed monthly"
        else "USD · ${if (this == STUDIO) "$72" else "$180"} billed yearly"
    fun priceNarration(cycle: PlanCycle): String {
        if (this == STARTER) return "Starter, free"
        val amount = if (this == STUDIO) (if (cycle == PlanCycle.MONTHLY) 8 else 6) else (if (cycle == PlanCycle.MONTHLY) 18 else 15)
        return "$title, $amount USD per month, ${if (cycle == PlanCycle.MONTHLY) "billed monthly" else "${if (this == STUDIO) 72 else 180} USD billed yearly"}"
    }
}
/** Local choice is a draft, not entitlement. Only an admitted review application changes the current allowance. */
internal data class PlanValues(val selected: PlanTier = PlanTier.STARTER, val cycle: PlanCycle = PlanCycle.MONTHLY,
    val current: PlanTier = PlanTier.STARTER, val currentCycle: PlanCycle = PlanCycle.MONTHLY, val used: Int = 3,
    val enabled: Boolean = true, val pending: Boolean = false, val reviewed: PlanTier? = null,
    val reviewedCycle: PlanCycle = PlanCycle.MONTHLY, val reviews: Int = 0, val applied: Int = 0) {
    val canEdit get() = enabled && !pending
    val canReview get() = canEdit && selected.available && (selected != current || cycle != currentCycle)
    val canApplyReviewed get() = canEdit && reviewed?.available == true && reviewed == selected && reviewedCycle == cycle && (reviewed != current || reviewedCycle != currentCycle)
    val canExport get() = canEdit && used < current.quota
    val usageDetail get() = if (used < current.quota) "${current.quota - used} ${if (current.quota - used == 1) "export" else "exports"} remaining" else if (used == current.quota) "You've reached the current allowance."
        else "Current usage exceeds this plan's allowance."
    fun select(tier: PlanTier) = if (canEdit && tier.available) copy(selected = tier) else this
    fun chooseCycle(value: PlanCycle) = if (canEdit) copy(cycle = value) else this
    fun review() = if (canReview) copy(reviewed = selected, reviewedCycle = cycle, reviews = reviews + 1) else this
    fun applyReviewed() = if (canApplyReviewed && reviewed != null) copy(current = reviewed, currentCycle = reviewedCycle, applied = applied + 1) else this
    fun export() = if (canExport) copy(used = used + 1) else this
    fun reachLimit() = if (canEdit) copy(used = maxOf(used, current.quota)) else this
    fun resetUsage() = if (canEdit) copy(used = 0) else this
}
@Composable
internal fun PlanExamples(values: PlanValues, onChange: (PlanValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Plans and allowances", subtitle = "Compare features, choose a plan and project current usage.")
        NavLink("Open plans preview", onPreview, subtitle = "Try billing choices, review and quota states")
        UsageMeter("Workspace members", "Unlimited", "Workspace members, unlimited", detail = "An unmetered allowance can omit the bar.")
    }
    PlanContent(values, onChange)
}
@Composable
internal fun PlanPreviewScreen(values: PlanValues, onChange: (PlanValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Plans preview", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("plans-scroll").padding(20.dp)) { PlanContent(values, onChange) }
    }
}
@Composable
internal fun PlanContent(values: PlanValues, onChange: (PlanValues) -> Unit) {
    val t = FoundryTheme.tokens
    var showReview by remember { mutableStateOf(false) }
    LaunchedEffect(values.canApplyReviewed) { if (!values.canApplyReviewed) showReview = false }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable plan actions", values.enabled, { onChange(values.copy(enabled = it)) })
            ToggleField("Show pending plan change", values.pending, { onChange(values.copy(pending = it)) }, enabled = values.enabled)
            SelectField("Billing choice", PlanCycle.entries, values.cycle, { onChange(values.chooseCycle(it)) }, enabled = values.canEdit, label = { it.label })
            Text("Local plan preview. Applying a choice changes this preview's allowance.", style = t.typography.caption)
            KeyValueRow("Current plan", "${values.current.title} · ${values.currentCycle.label}")
            KeyValueRow("Selected plan", "${values.selected.title} · ${values.cycle.label}")
            ActionButton({ val next = values.review(); onChange(next); showReview = next.canApplyReviewed }, isBusy = values.pending, enabled = values.canReview) { Text("Review plan change") }
            Text("Plan reviews: ${values.reviews} · Changes applied: ${values.applied}", style = t.typography.caption)
            Text("Last reviewed: ${values.reviewed?.let { "${it.title} · ${values.reviewedCycle.label}" } ?: "None"}", style = t.typography.caption)
        }
        Card {
            UsageMeter("Monthly exports", "${values.used} of ${values.current.quota}", "Monthly exports, ${values.used} of ${values.current.quota}, ${values.usageDetail}",
                detail = values.usageDetail, fraction = values.used.toFloat() / values.current.quota)
            ActionButton({ onChange(values.export()) }, variant = ButtonVariant.SECONDARY, enabled = values.canExport) { Text("Simulate export") }
            ActionButton({ onChange(values.reachLimit()) }, variant = ButtonVariant.QUIET, enabled = values.canEdit) { Text("Reach current limit") }
            ActionButton({ onChange(values.resetUsage()) }, variant = ButtonVariant.QUIET, enabled = values.canEdit) { Text("Reset usage") }
        }
        PlanTier.entries.forEach { tier -> key(tier) {
            PlanCard(tier.title, values.selected == tier, if (tier.available) "${if (values.selected == tier) "Selected" else "Choose"} ${tier.title}" else "${tier.title} unavailable",
                { onChange(values.select(tier)) }, subtitle = tier.subtitle, enabled = values.canEdit && tier.available,
                price = { PriceLabel(tier.price(values.cycle), tier.priceNarration(values.cycle), detail = tier.priceDetail(values.cycle)) },
                status = { Badge(if (tier == values.current) "Current plan" else if (tier == PlanTier.TEAM) "Coming soon" else if (tier == PlanTier.STUDIO) "More room" else "Essentials", tone = if (tier.available) MessageTone.NEUTRAL else MessageTone.WARNING) },
                features = {
                    FeatureRow("Monthly exports", "Included", true, "${tier.title}, ${tier.quota} monthly exports included", detail = "${tier.quota} each month")
                    FeatureRow("Offline drafts", "Included", true, "${tier.title}, offline drafts included")
                    FeatureRow("Shared workspaces", if (tier == PlanTier.TEAM) "Included" else "Not included", tier == PlanTier.TEAM,
                        "${tier.title}, shared workspaces ${if (tier == PlanTier.TEAM) "included" else "not included"}")
                })
        } }
    }
    SheetPanel("Plan review", showReview, { showReview = false }, "Close plan review", modifier = Modifier.testTag("plan-review")) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            values.reviewed?.let { tier ->
                SectionHeader("Review ${tier.title}", subtitle = values.reviewedCycle.label)
                PriceLabel(tier.price(values.reviewedCycle), tier.priceNarration(values.reviewedCycle), detail = tier.priceDetail(values.reviewedCycle))
                Text("Usage stays at ${values.used}. The preview allowance becomes ${tier.quota} exports per month.", style = t.typography.body)
                ActionButton({ onChange(values.applyReviewed()) }, enabled = values.canApplyReviewed) { Text("Apply preview change") }
            }
        }
    }
}
