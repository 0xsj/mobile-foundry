package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.charts.barchart.BarChart
import dev.mobilefoundry.ui.components.charts.barchart.ChartBar
import dev.mobilefoundry.ui.components.charts.legend.LegendItem
import dev.mobilefoundry.ui.components.charts.legend.LegendMark
import dev.mobilefoundry.ui.components.charts.progressring.ProgressRing
import dev.mobilefoundry.ui.components.charts.sparkline.Sparkline
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.trendbadge.TrendBadge
import dev.mobilefoundry.ui.components.display.trendbadge.TrendDirection
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.stepper.ValueStepper
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.navigation.tabs.Tabs
import dev.mobilefoundry.ui.components.patterns.chartpanel.ChartPanel
import dev.mobilefoundry.ui.components.patterns.disclosure.DisclosureSection
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class InsightPeriod(val label: String) { WEEK("Week"), MONTH("Month") }
internal data class InsightsValues(val period: InsightPeriod = InsightPeriod.WEEK, val completed: Int = 14, val enabled: Boolean = true, val empty: Boolean = false) {
    val samples get() = if (empty) emptyList() else if (period == InsightPeriod.WEEK) listOf(18.0, 24.0, 21.0, 32.0, 30.0, 41.0, 44.0) else listOf(92.0, 108.0, 96.0, 124.0)
    val total get() = samples.sum().toInt()
    val comparison get() = if (period == InsightPeriod.WEEK) "Up 30 min versus previous week" else "Down 40 min versus previous month"
    val summary get() = if (samples.isEmpty()) "No focus samples for this period" else "${period.label} focus: $total minutes across ${samples.size} samples. First ${samples.first().toInt()} minutes; last ${samples.last().toInt()} minutes."
    val bars get(): List<ChartBar> {
        if (empty) return emptyList()
        val factor = if (period == InsightPeriod.WEEK) 1 else 2
        return listOf("Design" to 84, "Reading" to 70, "Practice" to 56).map { (label, base) -> ChartBar(label, label, (base * factor).toDouble(), "${base * factor} min") }
    }
    fun setCompleted(value: Int) = if (enabled && value in 0..20) copy(completed = value) else this
    fun resetGoal() = if (enabled) copy(completed = 14) else this
}
@Composable
internal fun InsightsExamples(values: InsightsValues, onChange: (InsightsValues) -> Unit, onPreview: () -> Unit) {
    Card {
        Text("Insights and goals", style = FoundryTheme.tokens.typography.heading)
        Text("Small charts, readable values and your own dashboard controls.")
        NavLink("Open insights dashboard", onPreview, subtitle = "Trends, category bars and a session goal")
    }
    InsightsContent(values, onChange)
}
@Composable
internal fun InsightsPreviewScreen(values: InsightsValues, onChange: (InsightsValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Insights dashboard", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("insight-scroll").padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.section)) { InsightsContent(values, onChange) }
    }
}
@Composable
private fun InsightsContent(values: InsightsValues, onChange: (InsightsValues) -> Unit) {
    val t = FoundryTheme.tokens
    var showValues by rememberSaveable { mutableStateOf(false) }
    Card(role = SurfaceRole.FLOATING) {
        Tabs("Insight period", InsightPeriod.entries, values.period, { onChange(values.copy(period = it)) }, label = { it.label })
        ToggleField("Show empty insight data", values.empty, { onChange(values.copy(empty = it)) })
        ToggleField("Enable goal controls", values.enabled, { onChange(values.copy(enabled = it)) })
    }
    ChartPanel("Focus activity", subtitle = "Relative shape; equally spaced samples, no shared vertical scale.", plot = {
        if (values.empty) EmptyState("No focus activity", "Choose a period with samples to compare activity.")
        else {
            Text("${values.total} min", style = t.typography.title)
            Sparkline(values.samples, values.summary, height = 96.dp)
        }
    }, legend = { LegendItem(if (values.period == InsightPeriod.WEEK) "Daily focus minutes" else "Weekly focus minutes", mark = LegendMark.LINE) }, footer = {
        if (!values.empty) {
            TrendBadge(values.comparison, if (values.period == InsightPeriod.WEEK) TrendDirection.UP else TrendDirection.DOWN, tone = MessageTone.INFO)
            DisclosureSection("Show focus values", showValues, { showValues = it }, if (showValues) "Values shown" else "Values hidden") {
                Text(values.samples.joinToString(", ") { it.toInt().toString() } + " minutes")
            }
        }
    })
    ChartPanel("Time by category", subtitle = "Scale: 0–200 minutes. Same scale for both periods.", plot = {
        if (values.empty) Text("No category values for this period.")
        else BarChart("Focus minutes by category", values.bars, 200.0)
    }, legend = { LegendItem("Focus minutes", mark = LegendMark.SQUARE) })
    ChartPanel("Session goal", subtitle = "An independent local target; changing period keeps this value.", role = SurfaceRole.FLOATING, plot = {
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            ProgressRing("Completed sessions", values.completed / 20.0, "${values.completed} of 20")
        }
    }, legend = { LegendItem("Completed"); LegendItem("Remaining", color = t.colors.line.color, mark = LegendMark.SQUARE) }, footer = {
        ValueStepper("Completed session count", values.completed, { onChange(values.setCompleted(it)) }, "${values.completed} sessions",
            "Decrease completed sessions", "Increase completed sessions", range = 0..20, enabled = values.enabled)
        ActionButton({ onChange(values.resetGoal()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Reset session goal") }
        Text("Local preview data. No analytics source or activity tracking.", style = t.typography.caption)
    })
}
