package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.daterange.DateRangeField
import dev.mobilefoundry.ui.components.forms.timepicker.ClockTime
import dev.mobilefoundry.ui.components.forms.timepicker.TimeField
import dev.mobilefoundry.ui.components.navigation.daystrip.DayOption
import dev.mobilefoundry.ui.components.navigation.daystrip.DayStrip
import dev.mobilefoundry.ui.components.patterns.agendarow.AgendaRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SchedulingComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun dateRangeEndpointsCommitIndependentlyAndDisabledDateDraftIsDiscarded() {
        var start by mutableLongStateOf(SchedulingValues.date(0))
        var end by mutableLongStateOf(SchedulingValues.date(6))
        var enabled by mutableStateOf(true)
        var commits = 0
        compose.setContent { FoundryTheme {
            DateRangeField("Available dates", start, end, { start = it; commits++ }, { end = it; commits++ },
                "Start date", "End date", "Use date", "Keep date", "Choose a date", enabled = enabled)
        } }
        fun changeStart() {
            compose.onNodeWithContentDescription("Start date").performClick()
            compose.onNode(hasText("Tuesday, October 6, 2026", substring = true) and hasClickAction()).performClick()
        }
        changeStart(); compose.onNodeWithText("Keep date").performClick()
        assertEquals(SchedulingValues.date(0), start); assertEquals(0, commits)
        changeStart(); compose.onNodeWithText("Use date").performClick()
        assertEquals(SchedulingValues.date(1), start); assertEquals(SchedulingValues.date(6), end); assertEquals(1, commits)
        compose.onNodeWithContentDescription("End date").performClick()
        compose.onNode(hasText("Wednesday, October 7, 2026", substring = true) and hasClickAction()).performClick()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("Use date").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start date").assertIsNotEnabled()
        compose.onNodeWithContentDescription("End date").assertIsNotEnabled()
        assertEquals(SchedulingValues.date(6), end); assertEquals(1, commits)
    }
    @Test fun timeInputDiscardsCancelAndCommitsOnlyConfirmWhileDisabledDraftCloses() {
        var time by mutableStateOf(ClockTime(9, 30))
        var enabled by mutableStateOf(true)
        var commits = 0
        compose.setContent { FoundryTheme {
            TimeField("Session time", time, { time = it; commits++ }, SchedulingValues.timeLabel(time), "Use time", "Keep time", enabled = enabled)
        } }
        fun open() { compose.onNodeWithContentDescription("Session time").performClick() }
        fun edit() {
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
            compose.onAllNodes(hasSetTextAction())[0].performTextClearance()
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("10")
            compose.onAllNodes(hasSetTextAction())[1].performTextClearance()
            compose.onAllNodes(hasSetTextAction())[1].performTextInput("45")
        }
        open(); edit(); compose.onNodeWithText("Keep time").performClick()
        assertEquals(ClockTime(9, 30), time); assertEquals(0, commits)
        open(); edit(); compose.onNodeWithText("Use time").performClick()
        assertEquals(ClockTime(10, 45), time); assertEquals(1, commits)
        open(); compose.runOnIdle { enabled = false }
        compose.onNodeWithText("Use time").assertDoesNotExist()
        compose.onNodeWithContentDescription("Session time").assertIsNotEnabled()
        assertEquals(1, commits)
    }
    @Test fun narrowLargeTextDayChoicesAndAgendaActionsRemainIndependent() {
        var selected by mutableStateOf<String?>("mon")
        var actions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FoundryTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("schedule-probe")) {
                        DayStrip("Choose a day", listOf(
                            DayOption("mon", "Monday", "5", "Monday, October 5"),
                            DayOption("tue", "Tuesday", "6", "Tuesday, October 6"),
                            DayOption("sun", "Sunday", "11", "Sunday, October 11, unavailable", "Unavailable", false)), selected, { selected = it })
                        AgendaRow("A longer session title", "09:30–10:30", detail = "A personal workspace with longer descriptive copy", actions = {
                            ActionButton({ actions++ }, Modifier.testTag("agenda-action")) { Text("Inspect session") }
                        })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Monday, October 5").assertIsSelected()
        compose.onNodeWithContentDescription("Tuesday, October 6").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Sunday, October 11, unavailable").assertIsNotEnabled()
        compose.onNodeWithTag("agenda-action").performScrollTo().performClick()
        assertEquals("tue", selected); assertEquals(1, actions)
        val parent = compose.onNodeWithTag("schedule-probe").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("agenda-action").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= parent.left && action.right <= parent.right && action.height >= 48 * compose.density.density)
    }
}
