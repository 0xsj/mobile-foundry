package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.daterange.DateRangeField
import dev.mobilefoundry.ui.components.forms.timepicker.ClockTime
import dev.mobilefoundry.ui.components.forms.timepicker.TimeField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.components.navigation.daystrip.DayOption
import dev.mobilefoundry.ui.components.navigation.daystrip.DayStrip
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.patterns.agendarow.AgendaRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale

internal data class SchedulingValues(val selected: String? = "0", val start: Long = date(0), val end: Long = date(6),
    val time: ClockTime = ClockTime(9, 30), val enabled: Boolean = true, val empty: Boolean = false,
    val appliedDay: String? = null, val appliedTime: ClockTime = ClockTime(9, 30), val applied: Int = 0) {
    companion object {
        fun date(day: Int) = 1_791_158_400_000L + day * 86_400_000L // Monday 2026-10-05, UTC fixture
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        fun timeLabel(value: ClockTime) = String.format(Locale.ROOT, "%02d:%02d", value.hour, value.minute)
    }
    val rangeError get() = if (start > end) "End date must be on or after start date." else null
    val options get() = dayNames.mapIndexed { index, name ->
        val available = index != 6 && date(index) in start..end
        DayOption(index.toString(), name.take(3), (5 + index).toString(),
            "$name, October ${5 + index}, 2026${if (available) "" else ", unavailable"}",
            detail = if (available) null else "Unavailable", enabled = available)
    }
    val selectedName get() = options.firstOrNull { it.id == selected }?.accessibilityLabel ?: "No day selected"
    val canApply get() = enabled && rangeError == null && options.any { it.id == selected && it.enabled }
    fun select(id: String) = if (enabled && options.any { it.id == id && it.enabled }) copy(selected = id) else this
    fun apply() = if (canApply) copy(appliedDay = selected, appliedTime = time, applied = applied + 1) else this
    fun resetDates() = if (enabled) copy(start = date(0), end = date(6)) else this
    fun reverseDates() = if (enabled) copy(start = date(5), end = date(1)) else this
}
@Composable
internal fun SchedulingExamples(values: SchedulingValues, onChange: (SchedulingValues) -> Unit, onPreview: () -> Unit) {
    Card {
        Text("Dates and agendas", style = FoundryTheme.tokens.typography.heading)
        Text("Pick a day, review an agenda and plan a local session.")
        NavLink("Open schedule planner", onPreview, subtitle = "Day choices, date ranges and native time input")
    }
    SchedulingContent(values, onChange)
}
@Composable
internal fun SchedulingPreviewScreen(values: SchedulingValues, onChange: (SchedulingValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Schedule planner", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("schedule-scroll").padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.section)) { SchedulingContent(values, onChange) }
    }
}
@Composable
private fun SchedulingContent(values: SchedulingValues, onChange: (SchedulingValues) -> Unit) {
    val t = FoundryTheme.tokens
    Card(role = SurfaceRole.FLOATING) {
        ToggleField("Enable schedule controls", values.enabled, { onChange(values.copy(enabled = it)) })
        ToggleField("Show empty agenda", values.empty, { onChange(values.copy(empty = it)) })
        DayStrip("October 5–11, 2026", values.options, values.selected, { onChange(values.select(it)) }, enabled = values.enabled)
        Text(values.selectedName, style = t.typography.caption)
    }
    Card {
        SectionHeader("Day agenda", subtitle = values.selectedName)
        if (values.empty || (values.selected != "0" && (values.appliedDay == null || values.appliedDay != values.selected))) {
            EmptyState("Your day is open", "Plan a focus session when you are ready.")
        } else {
            if (values.selected == "0") AgendaRow("Design review", "08:00–08:30", detail = "Studio · 30 minutes",
                status = { Badge("Confirmed", tone = MessageTone.INFO) })
            if (values.appliedDay != null && values.appliedDay == values.selected) {
                HorizontalDivider()
                AgendaRow("Focus session", SchedulingValues.timeLabel(values.appliedTime), detail = "Personal workspace",
                    status = { Badge("Planned locally") })
            }
        }
    }
    Card {
        DateRangeField("Available dates", values.start, values.end, { onChange(values.copy(start = it)) }, { onChange(values.copy(end = it)) },
            "Start date", "End date", "Use date", "Keep date", "Choose a date",
            help = "Inclusive dates. This preview uses UTC calendar dates.", error = values.rangeError, enabled = values.enabled)
        WrapLayout {
            ActionButton({ onChange(values.reverseDates()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Preview reversed dates") }
            ActionButton({ onChange(values.resetDates()) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled) { Text("Reset dates") }
        }
    }
    Card(role = SurfaceRole.FLOATING) {
        SectionHeader("Plan a focus session", subtitle = "Your draft is separate from the session in the agenda.")
        TimeField("Session time", values.time, { onChange(values.copy(time = it)) }, SchedulingValues.timeLabel(values.time), "Use time", "Keep time", enabled = values.enabled)
        Text("Draft time: ${SchedulingValues.timeLabel(values.time)}", style = t.typography.caption)
        ActionButton({ onChange(values.apply()) }, enabled = values.canApply) { Text("Apply session") }
        Text("Sessions applied: ${values.applied}", style = t.typography.caption)
        Text("Local examples. No calendar access, reminders or booking service.", style = t.typography.caption)
    }
}
