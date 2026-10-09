package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DiscoveryCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun topic(value: String) {
        compose.onNodeWithContentDescription("Topic").performClick()
        compose.onNode(hasText(value) and hasClickAction()).performClick()
    }
    private fun query(value: String) {
        val field = compose.onNode(hasSetTextAction()).performScrollTo()
        field.performTextReplacement(value)
        field.performImeAction()
    }
    @Test fun searchAndSavedIdentitySurviveRoutesThemesAndRestorationWhileFilterDraftsAreDiscarded() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Discovery"); click("Open search workspace")
        compose.onNodeWithContentDescription("Search for motion").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save Motion study").performScrollTo().performClick()
        compose.onNodeWithText("Records opened: 0").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Open Motion study. Graphics. Explore motion, light and material in a small scene.").performScrollTo().performClick()
        click("Filters"); topic("Writing"); click("Include archived")
        compose.onNodeWithText("Discard filters").performClick()
        compose.onNodeWithText("Applied topic: All topics").performScrollTo().assertExists()
        click("Filters"); topic("Writing"); click("Apply filters")
        compose.onNodeWithText("No matching records").performScrollTo().assertExists()
        compose.onNodeWithText("Saved records: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Motion study").performScrollTo().assertExists()
        click("Filters"); click("Reset filter draft")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Apply filters").assertDoesNotExist()
        compose.onNodeWithText("Applied topic: Writing").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Remove topic filter").performScrollTo().performClick()
        query(" NOTES "); query("metal"); query("motion"); query(" notes ")
        click("Clear search")
        compose.onNodeWithContentDescription("Search again for notes").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Search again for metal").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Search again for motion").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); click("Content"); click("Discovery")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open search workspace")
        compose.onNodeWithContentDescription("Unsave Motion study").performScrollTo().assertExists()
        compose.onNodeWithText("Records opened: 1").performScrollTo().assertExists()
        click("Clear recent searches")
        compose.onNodeWithText("Recent searches").assertDoesNotExist()
        click("Enable discovery actions")
        compose.onNodeWithContentDescription("Search for motion").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Search library").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Unsave Motion study").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Saved records: 1").performScrollTo().assertExists()
    }
    @Test fun losingAvailabilityDismissesDraftsAndHiddenResultsCannotDispatchActions() {
        var values by mutableStateOf(DiscoveryValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { DiscoveryContent(values, { values = it }) } } }
        click("Filters"); topic("Design"); click("Include archived")
        compose.runOnIdle { values = values.copy(enabled = false) }
        compose.onNodeWithText("Apply filters").assertDoesNotExist()
        assertEquals(DiscoveryFilters(), values.filters)
        compose.runOnIdle { values = values.copy(enabled = true) }
        click("Filters"); topic("Design"); click("Include archived"); click("Apply filters")
        assertEquals(listOf("sketch", "interface"), values.results.map { it.id })
        compose.runOnIdle {
            values = values.toggleSaved("sketch").open("sketch").setQuery("missing")
            values = values.open("sketch").toggleSaved("sketch")
        }
        compose.onNodeWithText("No matching records").performScrollTo().assertExists()
        assertEquals(listOf("sketch"), values.saved); assertEquals("Sketch archive", values.openedTitle); assertEquals(1, values.opens)
        compose.runOnIdle { values = values.copy(enabled = false).setQuery("").useSuggestion("notes").apply(DiscoveryFilters()).clearRecent() }
        assertEquals("missing", values.query); assertEquals(DiscoveryTopic.DESIGN, values.filters.topic)
    }
}
