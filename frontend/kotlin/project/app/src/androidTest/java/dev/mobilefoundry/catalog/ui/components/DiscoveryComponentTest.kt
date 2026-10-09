package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.display.highlightedtext.HighlightedText
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.patterns.searchresultrow.SearchResultRow
import dev.mobilefoundry.ui.components.patterns.searchsuggestionrow.SearchSuggestionRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DiscoveryComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeActionsAreIndependentAndDisabledSearchPreservesControlledText() {
        var enabled by mutableStateOf(true)
        var opens = 0; var saves = 0; var suggestions = 0; var edits = 0; var submits = 0
        val text = "👨‍👩‍👧‍👦 café <b>Notes</b> & notes"
        val segments = discoverySegments(text, " notes ")
        assertEquals(text, segments.joinToString("") { it.text })
        assertEquals(listOf("Notes"), segments.filter { it.highlighted }.map { it.text })
        compose.setContent { FoundryTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            SearchField("Search library", "notes", { edits++ }, "Clear search", enabled = enabled, onSubmit = { submits++ })
            SearchSuggestionRow("notes", "Search for notes", { suggestions++ }, enabled = enabled, leading = { Text("Decorative suggestion") })
            HighlightedText(segments)
            SearchResultRow("Metal notes", "Open Metal notes", { opens++ }, enabled = enabled,
                preview = { Text("Passive excerpt") }, actions = { ActionButton({ saves++ }) { Text("Save result") } })
        } } }
        compose.onNodeWithText(text).assertExists().assertHasNoClickAction()
        compose.onNodeWithText("Decorative suggestion").assertDoesNotExist()
        compose.onNodeWithText("Passive excerpt").assertDoesNotExist()
        compose.onNodeWithText("Save result").performScrollTo().performClick()
        assertEquals(0, opens); assertEquals(1, saves)
        compose.onNodeWithContentDescription("Open Metal notes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Search for notes").performScrollTo().performClick()
        assertEquals(1, opens); assertEquals(1, suggestions)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithContentDescription("Open Metal notes").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Search for notes").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Search library").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Clear search").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Save result").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, opens); assertEquals(1, suggestions); assertEquals(2, saves)
        assertEquals(0, edits); assertEquals(0, submits)
    }
    @Test fun discoveryRowsGrowAtNarrowWidthLargeTextAndRTLWithUsableActionBounds() {
        var fontScale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var rowHeight = 0; var opens = 0; var saves = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("discovery-probe")) {
                    SearchSuggestionRow("Explore longer interface patterns", "Search for interface patterns", {}, detail = "Search suggestions wrap with their supplied text")
                    SearchResultRow("A longer library result title", "Open the design record", { opens++ }, Modifier.onSizeChanged { rowHeight = it.height },
                        detail = "Design · Archived", preview = { HighlightedText(discoverySegments("Read literal text and keep the complete sentence readable.", "literal text")) },
                        actions = { ActionButton({ saves++ }, Modifier.testTag("save")) { Text("Save result") } })
                }
            } }
        }
        compose.waitForIdle(); val normal = rowHeight
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(rowHeight > normal + 40)
        val viewport = compose.onNodeWithTag("discovery-probe").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("save").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        val action = compose.onNodeWithTag("save").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= viewport.left - 1f && action.right <= viewport.right + 1f)
        compose.onNodeWithContentDescription("Open the design record").performScrollTo().performClick()
        assertEquals(1, opens); assertEquals(1, saves)
    }
}
