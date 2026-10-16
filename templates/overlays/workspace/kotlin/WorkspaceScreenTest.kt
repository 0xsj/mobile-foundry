package dev.mobilefoundry.product

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.product.screens.ProductContent
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun compactNavigationMaterialAndRecreation() {
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithTag("project-two").performClick()
        compose.onNodeWithText("Your project starts here.").assertIsDisplayed()
        compose.onNodeWithText("Glass").assertIsOn().performClick().assertIsOff()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Project overview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Project two") and hasAnyAncestor(hasTestTag("workspace-detail"))).assertIsDisplayed()
        compose.onNodeWithText("Glass").assertIsOff()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("workspace-list").assertIsDisplayed()
        compose.onNodeWithTag("project-two").performClick()
        compose.onNodeWithTag("workspace-back").performClick()
        compose.onNodeWithTag("project-three").performClick()
        compose.onNodeWithText("Project three").assertIsDisplayed()
    }

    @Test fun expandedWidthReflowAndLargeTextKeepSelection() {
        var width by mutableStateOf(800.dp)
        var largeText by mutableStateOf(false)
        var state by mutableStateOf(ProductState())
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            // Density 1 makes an actual 800dp viewport fit this test device's pixels.
            CompositionLocalProvider(LocalDensity provides Density(1f, if (largeText) 1.5f else 1f)) {
                FoundryTheme { Box(Modifier.requiredSize(width, 600.dp)) { ProductContent(state, { state = it }) } }
            }
        } }
        compose.onNodeWithTag("workspace-list").assertIsDisplayed()
        compose.onNodeWithTag("workspace-empty").assertIsDisplayed()
        compose.onNodeWithTag("project-two").performClick()
        compose.onNodeWithTag("workspace-list").assertIsDisplayed()
        compose.onNodeWithTag("workspace-detail").assertIsDisplayed()
        compose.onNodeWithTag("workspace-back").assertDoesNotExist()
        compose.runOnIdle { width = 390.dp }
        compose.onNodeWithTag("workspace-list").assertDoesNotExist()
        compose.onNodeWithTag("workspace-back").assertIsDisplayed()
        compose.onNode(hasText("Project two") and hasAnyAncestor(hasTestTag("workspace-detail"))).assertIsDisplayed()
        compose.runOnIdle { width = 800.dp; largeText = true }
        compose.onNodeWithTag("workspace-list").assertDoesNotExist()
        compose.onNodeWithTag("workspace-back").performClick()
        compose.onNodeWithTag("workspace-list").assertIsDisplayed()
        compose.runOnIdle { largeText = false }
        compose.onNodeWithTag("workspace-detail").assertIsDisplayed()
        compose.onNode(hasText("Project two") and hasAnyAncestor(hasTestTag("workspace-detail"))).assertIsDisplayed()
    }

    @Test fun unknownSelectionAndBackPreserveIdentity() {
        val initial = ProductState()
        assertEquals(initial, initial.select("unknown"))
        val selected = initial.select("project-two").copy(glass = false).back()
        assertEquals(ProductProject.TWO, selected.selectedProject)
        assertFalse(selected.detailPresented); assertFalse(selected.glass)
        assertEquals(selected, selected.select("unknown"))
        assertTrue(selected.select("project-two").detailPresented)
    }
}
