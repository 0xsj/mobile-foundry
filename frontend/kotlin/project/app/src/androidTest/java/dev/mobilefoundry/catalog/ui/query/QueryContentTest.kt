package dev.mobilefoundry.catalog.ui.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.kernel.FailureMeta
import dev.mobilefoundry.query.QueryState
import dev.mobilefoundry.ui.QueryContent
import dev.mobilefoundry.ui.QueryCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueryContentTest {
    @get:Rule val compose = createComposeRule()
    private val copy = QueryCopy("Idle copy", "Loading copy", "Refreshing copy", "Empty copy")
    private val failure = Failure.Unavailable(FailureMeta("Unavailable copy"))

    @Test fun presentationMatrixDistinguishesNoSnapshotFromEmptySnapshot() {
        val state = mutableStateOf<QueryState<String>>(QueryState.Idle)
        compose.setContent { FoundryCatalogTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                QueryContent(state.value, copy, refresh = {}, cancel = {}, isEmpty = { it.isEmpty() }) { Text(it) }
            }
        } }
        data class Case(val state: QueryState<String>, val status: String?, val snapshot: String?, val loading: Boolean, val failed: Boolean)
        val cases = listOf(
            Case(QueryState.Idle, "Idle copy", null, false, false),
            Case(QueryState.Loading(null), "Loading copy", null, true, false),
            Case(QueryState.Loaded("Saved"), null, "Saved", false, false),
            Case(QueryState.Loaded(""), null, "Empty copy", false, false),
            Case(QueryState.Loading("Saved"), "Refreshing copy", "Saved", true, false),
            Case(QueryState.Loading(""), "Refreshing copy", "Empty copy", true, false),
            Case(QueryState.Failed(failure, null), "Unavailable copy", null, false, true),
            Case(QueryState.Failed(failure, "Saved"), "Unavailable copy", "Saved", false, true),
            Case(QueryState.Failed(failure, ""), "Unavailable copy", "Empty copy", false, true),
        )
        for (case in cases) {
            compose.runOnIdle { state.value = case.state }
            for (text in listOf("Idle copy", "Loading copy", "Refreshing copy", "Unavailable copy", "Saved", "Empty copy")) {
                if (text == case.status || text == case.snapshot) compose.onNodeWithText(text).assertExists()
                else compose.onNodeWithText(text).assertDoesNotExist()
            }
            if (case.loading) {
                compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
                compose.onNodeWithText("Cancel loading").assertExists()
                compose.onNodeWithContentDescription(case.status!!).assertExists()
            } else {
                compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
                compose.onNodeWithText("Cancel loading").assertDoesNotExist()
            }
            if (case.failed) compose.onNodeWithText("Retry").assertExists()
            else compose.onNodeWithText("Retry").assertDoesNotExist()
            compose.onNodeWithText("Refresh").assertExists()
        }
    }

    @Test fun callbacksOnlyRunOnTheirUserActions() {
        val state = mutableStateOf<QueryState<String>>(QueryState.Loading("Saved"))
        var refreshes = 0
        var cancellations = 0
        compose.setContent { FoundryCatalogTheme {
            QueryContent(state.value, copy, refresh = { refreshes++ }, cancel = { cancellations++ }) { Text(it) }
        } }
        compose.runOnIdle { assertEquals(0, refreshes); assertEquals(0, cancellations) }
        compose.onNodeWithText("Refresh").performClick()
        compose.onNodeWithText("Cancel loading").performClick()
        compose.runOnIdle { state.value = QueryState.Failed(failure, "Saved") }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(2, refreshes); assertEquals(1, cancellations) }
    }

    @Test fun internalFailureOnlyShowsPublicCopy() {
        compose.setContent { FoundryCatalogTheme {
            QueryContent<String>(QueryState.Failed(Failure.Internal(FailureMeta("Private diagnostic detail")), null),
                copy, refresh = {}, cancel = {}) { Text(it) }
        } }
        compose.onNodeWithText("An unexpected error occurred.").assertIsDisplayed()
        compose.onNodeWithText("Private diagnostic detail").assertDoesNotExist()
    }

    @Test fun galleryNavigationAndEmptyRefreshActionsWork() {
        compose.setContent { FoundryCatalogTheme { MainNavigation() } }
        compose.onNodeWithText("Async UI patterns").performClick()
        compose.onNodeWithText("Empty").performScrollTo().performClick()
        compose.onNodeWithText("No workspace content.").assertExists()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Refreshing workspace…").assertExists()
        compose.onNodeWithText("No workspace content.").assertExists()
        compose.onNodeWithText("Cancel loading").performScrollTo().performClick()
        compose.onNodeWithText("No workspace content.").assertExists()
        compose.onNodeWithText("Refreshing workspace…").assertDoesNotExist()
    }
}
