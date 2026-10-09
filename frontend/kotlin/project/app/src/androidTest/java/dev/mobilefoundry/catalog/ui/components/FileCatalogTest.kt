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

class FileCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun family(text: String) { compose.onNodeWithContentDescription("Component families").performScrollTo(); click(text) }
    private fun action(label: String) { compose.onNodeWithContentDescription(label).performScrollTo().performClick() }
    private fun query(text: String) { compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(text); compose.onNode(hasSetTextAction()).performImeAction() }
    @Test fun searchingRetainsExpansionAndSelectionAcrossRestorationNavigationAndThemes() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        family("Commerce"); action("Increase Pocket notebook")
        family("Files"); click("Open files preview"); click("Collapse all folders")
        compose.onNodeWithText("3 visible items").performScrollTo().assertExists()
        query(" FIELD ")
        compose.onNodeWithContentDescription("Collapse References").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("5 visible items").performScrollTo().assertExists()
        action("Favorite Field image.jpg")
        action("Open Field image.jpg, JPEG · 860 KB, Level 4")
        compose.onNode(hasText("Path") and hasText("Atlas / Studies / References / Field image.jpg") and hasAnyAncestor(hasTestTag("file-inspector"))).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Close file inspector").assertDoesNotExist()
        compose.onNodeWithText("Last opened: Field image.jpg").performScrollTo().assertExists()
        compose.onNodeWithText("File opens: 1 · Favorites: 1").assertExists()
        click("Clear file search")
        compose.onNodeWithText("3 visible items").performScrollTo().assertExists()
        action("Expand Atlas"); action("Expand Studies"); action("Expand References")
        compose.onNodeWithContentDescription("Open Field image.jpg, JPEG · 860 KB, Level 4").performScrollTo().assertIsSelected()
        action("Collapse Atlas")
        compose.onNodeWithText("Last opened: Field image.jpg").performScrollTo().assertExists()
        click("Show empty browser"); compose.onNodeWithText("No files yet").performScrollTo().assertExists()
        click("Show empty browser")
        compose.onNodeWithText("File opens: 1 · Favorites: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Content"); family("Files")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open files preview")
        query("field image"); action("Open Field image.jpg, JPEG · 860 KB, Level 4")
        compose.onNodeWithText("Remove inspector favorite").performClick()
        compose.onNodeWithText("Close file inspector").performClick()
        compose.onNodeWithText("File opens: 2 · Favorites: 0").performScrollTo().assertExists()
        compose.onNodeWithText("Back to components").performClick(); family("Commerce")
        compose.onNode(hasText("Total") and hasText("$46.00")).performScrollTo().assertExists()
    }
    @Test fun unknownHiddenUnavailableEmptyAndDisabledCommandsAreRejectedAndInspectorCloses() {
        var values by mutableStateOf(FileBrowserValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { FileContent(values, { values = it }) } } }
        val initial = values
        compose.runOnIdle { values = values.open("field").open("missing").open("locked").favorite("locked").favorite("missing").toggle("lens") }
        assertEquals(initial, values)
        compose.onNodeWithContentDescription("Unavailable Restricted.txt, Unavailable in this preview, Level 1").performScrollTo().assertIsNotEnabled().performClick()
        action("Open Field notes.md, Markdown · 2 KB, Level 1")
        compose.onNodeWithText("Close file inspector").assertExists()
        compose.runOnIdle { values = values.copy(empty = true) }
        compose.onNodeWithText("Close file inspector").assertDoesNotExist()
        val empty = values
        compose.runOnIdle { values = values.open("notes").favorite("notes").toggle("atlas").collapseAll() }
        assertEquals(empty, values)
        compose.runOnIdle { values = values.copy(empty = false, enabled = false) }
        val disabled = values
        compose.runOnIdle { values = values.search("field").resetBrowser().open("notes").favorite("notes").toggle("atlas").collapseAll() }
        assertEquals(disabled, values)
        compose.onNodeWithContentDescription("Open Field notes.md, Markdown · 2 KB, Level 1").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { values = values.copy(enabled = true).collapseAll().search("missing") }
        assertTrue(values.canInspect); assertTrue(values.entries.isEmpty())
        compose.onNodeWithText("No matching files").performScrollTo().assertExists()
        compose.runOnIdle { values = values.favorite("notes").resetBrowser() }
        assertEquals(listOf("notes"), values.favorites); assertEquals(1, values.opens); assertNull(values.selected)
        assertEquals("Atlas / Studies / References / Field image.jpg", BrowserItem.find("field")?.path)
    }
}
