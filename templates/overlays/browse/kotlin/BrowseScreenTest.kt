package dev.mobilefoundry.product

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BrowseScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun searchDetailMaterialAndRecreationKeepFilter() {
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithTag("browse-search").performTextInput("TWO")
        compose.onNodeWithTag("collection-one").assertDoesNotExist()
        compose.onNodeWithTag("collection-two").performClick()
        compose.onNodeWithText("Your collection starts here.").assertIsDisplayed()
        compose.onNodeWithTag("browse-search").assertDoesNotExist()
        compose.onNodeWithText("Glass").assertIsOn().performClick().assertIsOff()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Collection overview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Collection two").assertIsDisplayed()
        compose.onNodeWithText("Glass").assertIsOff()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("browse-search").assertTextContains("TWO")
        compose.onNodeWithText("Clear search").performClick()
        compose.onNodeWithTag("collection-one").assertIsDisplayed()
        compose.onNodeWithTag("browse-search").performTextInput("absent")
        compose.onNodeWithText("No collections match your search.").assertIsDisplayed()
        compose.onNodeWithText("Clear search").performClick()
        compose.onNodeWithTag("collection-three").performScrollTo().performClick()
        compose.onNodeWithText("Collection three").assertIsDisplayed()
        compose.onNodeWithTag("browse-back").performClick()
        compose.onNodeWithTag("browse-search").assertTextContains("Find collections")
        compose.onNodeWithText("Glass").assertIsOff()
    }

    @Test fun filteringAdmissionAndBackKeepSearch() {
        val initial = ProductState()
        assertEquals(3, initial.visibleCollections.size)
        val filtered = initial.copy(filter = "  TWO  ")
        assertEquals(listOf(ProductCollection.TWO), filtered.visibleCollections)
        assertEquals(filtered, filtered.select("unknown"))
        val returned = filtered.select("collection-two").copy(glass = false).back()
        assertNull(returned.selectedCollection)
        assertEquals("  TWO  ", returned.filter); assertFalse(returned.glass)
        assertTrue(returned.copy(filter = "absent").visibleCollections.isEmpty())
        assertEquals(ProductCollection.entries, returned.copy(filter = "").visibleCollections)
    }
}
