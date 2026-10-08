package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.components.forms.stepper.ValueStepper
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class ValueStepperTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeButtonsClampIntegerExtremesAndDispatchOnlyOnActivation() {
        compose.setContent {
            var value by remember { mutableIntStateOf(Int.MAX_VALUE - 1) }
            var calls by remember { mutableIntStateOf(0) }
            FoundryTheme {
                Column {
                    ValueStepper("Bounded value", value, { value = it; calls++ }, value.toString(),
                        "Decrease value", "Increase value", range = Int.MIN_VALUE..Int.MAX_VALUE, step = Int.MAX_VALUE)
                    Text("Changes: $calls")
                }
            }
        }
        compose.onNodeWithContentDescription("Increase value").performClick()
        compose.onNodeWithText(Int.MAX_VALUE.toString()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase value").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Decrease value").performClick()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease value").performClick().performClick()
        compose.onNodeWithText(Int.MIN_VALUE.toString()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease value").assertIsNotEnabled()
        compose.onNodeWithText("Changes: 4").assertIsDisplayed()
    }
}
