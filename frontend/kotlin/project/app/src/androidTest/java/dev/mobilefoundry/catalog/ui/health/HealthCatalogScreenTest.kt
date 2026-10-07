package dev.mobilefoundry.catalog.ui.health

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme
import org.junit.Rule
import org.junit.Test

class HealthCatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    @Test fun healthyMalformedValidationRateLimitAndTimeout() {
        compose.setContent { FoundryCatalogTheme { HealthCatalogScreen(onBack = {}) } }
        waitFor("Status: ok")
        compose.onNodeWithText("Unavailable").performClick()
        waitFor("Service unavailable")
        compose.onNodeWithText("Malformed").performClick()
        waitFor("An unexpected error occurred.")
        compose.onNodeWithText("Validation").performScrollTo().performClick()
        waitFor("example: Required.")
        compose.onNodeWithText("Rate limited").performScrollTo().performClick()
        waitFor("Retry timing: 2000 ms")
        compose.onNodeWithText("Timeout").performScrollTo().performClick()
        waitFor("Request timed out")
    }
    @Test fun replacingRequestKeepsNewestScenario() {
        compose.setContent { FoundryCatalogTheme { HealthCatalogScreen(onBack = {}) } }
        waitFor("Status: ok")
        compose.onNodeWithText("Unavailable").performClick()
        compose.onNode(hasText("Healthy") and hasClickAction()).performScrollTo().performClick()
        waitFor("Status: ok")
        compose.onNodeWithText("Service unavailable").assertDoesNotExist()
    }
}
