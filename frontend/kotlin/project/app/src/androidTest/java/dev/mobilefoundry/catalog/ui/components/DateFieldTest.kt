package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.components.forms.datepicker.DateField
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test

class DateFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dateDraftCancelsOrCommitsWithoutShiftingInWesternTimeZone() {
        val previousZone = TimeZone.getDefault()
        val previousLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            Locale.setDefault(Locale.US)
            val october8 = 1791417600000L
            compose.setContent {
                var committed by remember { mutableLongStateOf(october8) }
                FoundryTheme {
                    Column {
                        DateField("Review date", committed, { committed = it }, "Use date", "Discard date", "Choose date")
                        Text("Committed: $committed")
                    }
                }
            }
            // UTC midnight remains October 8 rather than becoming October 7 locally.
            compose.onNodeWithText("Oct 8, 2026").assertIsDisplayed()
            compose.onNodeWithContentDescription("Review date").performClick()
            compose.onNode(hasText("Friday, October 9, 2026") and hasClickAction()).performClick()
            compose.onNodeWithText("Discard date").performClick()
            compose.onNodeWithText("Committed: $october8").assertIsDisplayed()
            compose.onNodeWithContentDescription("Review date").performClick()
            compose.onNode(hasText("Friday, October 9, 2026") and hasClickAction()).performClick()
            compose.onNodeWithText("Use date").performClick()
            compose.onNodeWithText("Committed: ${october8 + 86400000L}").assertIsDisplayed()
            compose.onNodeWithText("Oct 9, 2026").assertIsDisplayed()
        } finally {
            TimeZone.setDefault(previousZone)
            Locale.setDefault(previousLocale)
        }
    }
}
