package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.layout.splitpane.SplitPane
import dev.mobilefoundry.ui.components.navigation.breadcrumbs.*
import dev.mobilefoundry.ui.components.navigation.navigationrail.*
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun destinationsGuardDisabledActionsAndCurrentBreadcrumbIsPassive() {
        var selected by mutableStateOf<String?>("all")
        val actions = mutableListOf<String>()
        compose.setContent { FoundryTheme {
            Column {
                BreadcrumbTrail(listOf(BreadcrumbItem("root", "Workspace"), BreadcrumbItem("closed", "Unavailable", false),
                    BreadcrumbItem("current", "Atlas")), "Atlas, current location", { actions += it })
                DestinationRail("Projects", listOf(RailDestination("all", "All projects"), RailDestination("starred", "Starred"),
                    RailDestination("shared", "Shared", enabled = false)), selected, { selected = it; actions += it },
                    Modifier.width(130.dp).height(350.dp)) { Text("Icon") }
            }
        } }
        compose.onNodeWithContentDescription("All projects").assertIsSelected()
        compose.onNodeWithContentDescription("Shared").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Unavailable").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Atlas, current location").assertHasNoClickAction()
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("Starred").performClick().assertIsSelected()
        compose.onNodeWithText("Workspace").performClick()
        assertEquals(listOf("starred", "root"), actions)
    }
    @Test fun panesReflowByLocalWidthAndLargeTextWithPrimaryAtLogicalLeadingEdge() {
        var width by mutableStateOf(820.dp)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var fontScale by mutableFloatStateOf(1f)
        var detailPresented by mutableStateOf(false)
        compose.setContent {
            // A wide logical test viewport on the phone emulator; not a physical tablet observation.
            CompositionLocalProvider(LocalDensity provides Density(.4f, fontScale), LocalLayoutDirection provides direction) {
                FoundryTheme {
                    SplitPane(detailPresented, Modifier.width(width).height(420.dp).testTag("viewport"),
                        primary = { Box(Modifier.fillMaxSize().testTag("primary")) { Text("Projects") } },
                        detail = { Box(Modifier.fillMaxSize().testTag("detail")) { Text("Detail") } })
                }
            }
        }
        fun bounds(id: String) = compose.onNodeWithTag(id).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds("primary").right <= bounds("detail").left)
        compose.runOnIdle { direction = LayoutDirection.Rtl }
        assertTrue(bounds("primary").left >= bounds("detail").right)
        compose.runOnIdle { width = 280.dp }
        compose.onNodeWithTag("primary").assertExists(); compose.onNodeWithTag("detail").assertDoesNotExist()
        compose.runOnIdle { detailPresented = true }
        compose.onNodeWithTag("detail").assertExists(); compose.onNodeWithTag("primary").assertDoesNotExist()
        compose.runOnIdle { width = 820.dp; fontScale = 2f }
        compose.onNodeWithTag("detail").assertExists(); compose.onNodeWithTag("primary").assertDoesNotExist()
    }
    @Test fun featureSelectionSurvivesReflowAndHiddenCollectionsWithoutFallback() {
        var values by mutableStateOf(WorkspaceValues())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.4f)) { FoundryTheme { WorkspaceContent(values, { values = it }) } }
        }
        compose.onNode(hasText("Orbit study") and hasClickAction()).performClick()
        compose.onNodeWithText("Star this project").performClick()
        assertTrue("orbit" in values.starred)
        compose.runOnIdle { values = values.copy(forceSingle = true) }
        compose.onNodeWithTag("workspace-projects").assertDoesNotExist()
        compose.onNodeWithText("Back to projects").performClick()
        assertEquals("orbit", values.selected)
        compose.onNodeWithTag("workspace-projects").assertExists()
        compose.runOnIdle { values = values.copy(forceSingle = false).navigate("archived") }
        assertEquals("orbit", values.selected); assertNull(values.project)
        compose.onNodeWithText("Choose a project").assertExists()
        compose.runOnIdle { values = values.navigate("starred") }
        assertEquals("orbit", values.project?.id)
        compose.onNodeWithText("Star this project").assertIsOn()
    }
    @Test fun largeTextBreadcrumbsWrapAndRailTargetsRemainScrollable() {
        var selected by mutableStateOf<String?>("all")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("narrow-workspace")) {
                    BreadcrumbTrail(listOf(BreadcrumbItem("root", "Personal workspace"), BreadcrumbItem("project", "Atlas exploration")),
                        "Atlas exploration, current location", {})
                    DestinationRail("Projects", listOf(RailDestination("all", "All projects"), RailDestination("starred", "Starred")),
                        selected, { selected = it }, Modifier.width(130.dp).height(350.dp).testTag("rail")) { Text("Icon") }
                }
            } }
        }
        val viewport = compose.onNodeWithTag("narrow-workspace").fetchSemanticsNode().boundsInRoot
        val root = compose.onNode(hasText("Personal workspace") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val current = compose.onNodeWithContentDescription("Atlas exploration, current location").fetchSemanticsNode().boundsInRoot
        assertTrue(current.top >= root.bottom)
        assertTrue(root.left >= viewport.left && root.right <= viewport.right + 1f)
        assertTrue(current.left >= viewport.left && current.right <= viewport.right + 1f)
        compose.onNodeWithContentDescription("Starred").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Starred").assertHeightIsAtLeast(48.dp)
    }
}
