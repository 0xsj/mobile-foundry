package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.components.feedback.loadmore.LoadMorePhase
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivityCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun reveal(text: String) {
        compose.onNodeWithTag("activity-list").performScrollToNode(hasText(text))
    }
    private fun click(text: String) { reveal(text); compose.onNodeWithText(text).performClick() }
    private fun complete(values: ActivityPreviewValues) {
        compose.waitUntil(5_000) { !values.refreshing && values.phase != LoadMorePhase.LOADING }
        compose.waitForIdle()
    }
    @Test fun activityRouteRetainsFeedAcrossBackFamilyAndThemeChanges() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        fun galleryClick(label: String) {
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        }
        galleryClick("Activity"); galleryClick("Open activity preview")
        click("Read update 1"); click("Load more updates")
        compose.onNodeWithTag("activity-list").performScrollToIndex(1)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("6 updates · Refreshes: 0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Back to components").performClick()
        galleryClick("Content"); galleryClick("Activity")
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        galleryClick("Open activity preview")
        reveal("6 updates · Refreshes: 0")
        compose.onNodeWithText("6 updates · Refreshes: 0").assertIsDisplayed()
        reveal("Collapse update 1")
        compose.onNodeWithText("Collapse update 1").assertIsDisplayed()
    }
    @Test fun pagesRetainRowsOnFailureRetryAndRefreshPreservesSurvivingExpansion() {
        val values = ActivityPreviewValues()
        compose.setContent { FoundryTheme { ActivityPreviewScreen(values, {}) } }
        click("Read update 1")
        click("Fail next page"); click("Load more updates"); complete(values)
        assertEquals(listOf(1, 2, 3), values.records.map { it.id })
        reveal("Could not load more. Your updates are still here.")
        compose.onNodeWithText("Could not load more. Your updates are still here.").assertIsDisplayed()
        click("Retry page"); complete(values)
        assertEquals((1..6).toList(), values.records.map { it.id })
        click("Load more updates"); complete(values)
        reveal("You’re all caught up.")
        compose.onNodeWithText("You’re all caught up.").assertIsDisplayed()
        compose.onNodeWithText("Load more updates").assertDoesNotExist()
        click("Refresh updates"); complete(values)
        assertEquals(3, values.records.size); assertEquals(1, values.refreshes)
        reveal("Collapse update 1")
        compose.onNodeWithText("Collapse update 1").assertIsDisplayed()
        click("Collapse update 1")
        compose.onNodeWithText("Read update 1").assertIsDisplayed()
    }
    @Test fun nativePullGestureRunsRefreshAndRejectsDuplicateRequests() {
        val values = ActivityPreviewValues()
        compose.setContent { FoundryTheme { ActivityPreviewScreen(values, {}) } }
        compose.onNodeWithTag("activity-list").performTouchInput { swipeDown(startY = height * 0.2f, endY = height * 0.9f, durationMillis = 700) }
        compose.waitUntil(5_000) { values.refreshes == 1 }
        compose.runOnIdle { values.requestPage(); values.requestPage(); values.requestRefresh(); assertEquals(2, values.request) }
        complete(values)
        assertEquals(6, values.records.size); assertEquals(1, values.refreshes)
    }
    @Test fun leavingCancelsPendingPageAndReturningCanRetry() {
        val values = ActivityPreviewValues()
        var visible by mutableStateOf(true)
        compose.setContent { FoundryTheme { if (visible) ActivityPreviewScreen(values, {}) } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { values.requestPage() }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertEquals(LoadMorePhase.IDLE, values.phase); assertEquals(3, values.records.size)
        compose.runOnIdle { visible = true }
        compose.mainClock.autoAdvance = true
        click("Load more updates"); complete(values)
        assertEquals(6, values.records.size)
    }
}
