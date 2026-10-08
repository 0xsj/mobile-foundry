package dev.mobilefoundry.catalog.ui.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme
import dev.mobilefoundry.catalog.MainNavigation
import org.junit.Rule
import org.junit.Test

class NotesCatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun waitFor(text: String, scroll: Boolean = true) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        if (scroll) compose.onNodeWithText(text).performScrollTo()
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun bothProvidersUseTheSameContentEmptyAndFailureScreen() {
        compose.setContent { FoundryCatalogTheme { NotesCatalogScreen(onBack = {}) } }
        for (provider in listOf("Memory", "HTTP")) {
            compose.onNodeWithText(provider).performScrollTo().performClick()
            compose.onNodeWithText("Content").performScrollTo().performClick()
            waitFor("Sketch a native idea")
            compose.onNodeWithText("Empty").performScrollTo().performClick()
            waitFor("No notes yet.")
            compose.onNodeWithText("Unavailable").performScrollTo().performClick()
            waitFor("Notes are temporarily unavailable.")
            compose.onNodeWithText("Retry").performScrollTo().performClick()
            waitFor("Notes are temporarily unavailable.")
        }
    }

    @Test fun cancelAndProviderReplacementRemainUsable() {
        compose.setContent { FoundryCatalogTheme { NotesCatalogScreen(onBack = {}) } }
        waitFor("Sketch a native idea")
        compose.onNodeWithText("Slow").performScrollTo().performClick()
        waitFor("Cancel loading")
        compose.onNodeWithText("Cancel loading").performClick()
        waitFor("Ready to load notes.")
        compose.onNodeWithText("Refresh").performClick()
        waitFor("Cancel loading")
        compose.onNodeWithText("HTTP").performScrollTo().performClick()
        compose.onNodeWithText("Empty").performScrollTo().performClick()
        waitFor("No notes yet.")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Loading notes…").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Sketch a native idea").assertDoesNotExist()
    }

    @Test fun navigationEntryCanBePoppedDuringLoadingAndOpenedAgain() {
        compose.setContent { FoundryCatalogTheme { MainNavigation() } }
        compose.onNodeWithText("Notes service seam").performClick()
        waitFor("Sketch a native idea")
        compose.onNodeWithText("Slow").performScrollTo().performClick()
        waitFor("Cancel loading")
        compose.onNodeWithText("Back").performScrollTo().performClick()
        waitFor("Notes service seam", scroll = false)
        compose.onNodeWithText("Notes service seam").performClick()
        waitFor("Sketch a native idea")
    }
}
