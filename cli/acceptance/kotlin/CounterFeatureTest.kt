package dev.mobilefoundry.product

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CounterFeatureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun generatedProductRunsAnInjectedServiceQueryAndMutation() {
        compose.onNodeWithText("Counter: 0").assertIsDisplayed()
        repeat(2) { compose.onNodeWithText("Increment").performClick() }
        compose.onNodeWithText("Counter: 2").assertIsDisplayed()
        compose.onNodeWithText("Confirmed: 2").assertIsDisplayed()
    }
}
