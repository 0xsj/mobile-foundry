package dev.mobilefoundry.catalog.installations

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.catalog.MainActivity
import org.junit.Rule
import org.junit.Test

class PushTokenUITest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun unconfiguredProviderIsAnExplicitRecoverableUIState() {
        compose.onNode(hasText("Studio") and hasClickAction()).performClick()
        compose.onNode(hasText("Open catalog") and hasClickAction()).performClick()
        compose.onNode(hasText("System notifications") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("push.status").assertTextEquals("No collected token")
        compose.onNodeWithTag("push.failure").assertDoesNotExist()
        compose.onNodeWithTag("push.collect").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("push.failure").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("push.failure").assertTextContains("Push provider setup is missing.",substring=true)
        compose.onNodeWithTag("push.status").assertTextEquals("No collected token")
        compose.onNodeWithTag("push.register").assertDoesNotExist()
    }
}
