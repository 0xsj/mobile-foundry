package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.layout.datatable.*
import dev.mobilefoundry.ui.components.navigation.pagination.PaginationBar
import dev.mobilefoundry.ui.components.navigation.tablesortheader.*
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TableComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun sortAndPagesHaveIndependentStateAndDisabledEndpointsDoNotDispatch() {
        var page by mutableIntStateOf(1)
        var enabled by mutableStateOf(true)
        var order by mutableStateOf(TableSortOrder.ASCENDING)
        var sorts = 0; var pages = 0
        compose.setContent { FoundryTheme {
            Column {
                TableSortHeader("Project", order, if (order == TableSortOrder.ASCENDING) "Ascending" else "Descending",
                    { sorts++; order = TableSortOrder.DESCENDING }, enabled = enabled)
                PaginationBar(page, 3, "Page $page of 3", "Previous page", "Next page", { page = it; pages++ }, enabled = enabled)
            }
        } }
        compose.onNodeWithText("Previous page").assertIsNotEnabled().performClick()
        assertEquals(0, pages)
        compose.onNodeWithText("Project").performClick()
        compose.onNodeWithText("Project").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Descending"))
        assertEquals(1, sorts); assertEquals(1, page)
        compose.onNodeWithText("Next page").performClick(); compose.onNodeWithText("Next page").performClick()
        compose.onNodeWithText("Next page").assertIsNotEnabled().performClick()
        assertEquals(3, page); assertEquals(2, pages)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("Project").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Previous page").assertIsNotEnabled().performClick()
        assertEquals(1, sorts); assertEquals(2, pages)
    }
    @Test fun narrowTableScrollsAlignedColumnsAndKeepsCellActionsIndependent() {
        var inspected = 0
        lateinit var scroll: ScrollState
        compose.setContent { FoundryTheme {
            scroll = rememberScrollState()
            DataTable("Records", listOf("atlas"), listOf(DataTableColumn("name", "Project", 240.dp), DataTableColumn("open", "Inspect", 160.dp)),
                { it }, Modifier.width(240.dp).testTag("table"), scrollState = scroll, header = { column ->
                    Text(column.label, Modifier.fillMaxWidth().testTag("header:${column.id}"))
                }, cell = { _, column ->
                    if (column.id == "name") Text("Atlas workspace", Modifier.fillMaxWidth().testTag("name"))
                    else ActionButton({ inspected++ }, Modifier.fillMaxWidth().testTag("inspect")) { Text("Inspect Atlas") }
                })
        } }
        fun bounds(id: String) = compose.onNodeWithTag(id, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val before = bounds("name")
        assertEquals(before.left, bounds("header:name").left, 1f)
        compose.onNodeWithTag("inspect").performScrollTo().performClick()
        assertEquals(1, inspected)
        assertTrue(scroll.value > 0)
        compose.onNodeWithTag("inspect").assertIsDisplayed()
        assertEquals(bounds("inspect").left, bounds("header:open").left, 1f)
        compose.onNodeWithTag("inspect").assertHeightIsAtLeast(48.dp)
    }
    @Test fun largeTextRowsGrowAndPaginationWrapsWithinNarrowBoundsInRTL() {
        var scale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalLayoutDirection provides direction) {
                FoundryTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("viewport")) {
                        DataTable("Records", listOf("atlas"), listOf(DataTableColumn("name", "Project", 240.dp), DataTableColumn("status", "Status", 160.dp)),
                            { it }, Modifier.fillMaxWidth().testTag("table"), header = { Text(it.label, Modifier.fillMaxWidth().testTag("header:${it.id}")) },
                            cell = { _, column -> Text(if (column.id == "name") "A much longer project title that grows vertically" else "Ready for review", Modifier.fillMaxWidth().testTag("cell:${column.id}")) })
                        PaginationBar(1, 3, "Page 1 of 3", "Previous page", "Next page", {}, Modifier.testTag("pages"))
                    }
                }
            }
        }
        fun bounds(id: String) = compose.onNodeWithTag(id, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val normal = bounds("table").height
        compose.runOnIdle { scale = 2f; direction = LayoutDirection.Rtl }
        assertTrue(bounds("table").height > normal + 40f)
        assertTrue(bounds("header:name").left > bounds("header:status").left)
        val viewport = bounds("viewport"); val pages = bounds("pages")
        assertTrue(pages.left >= viewport.left - 1f && pages.right <= viewport.right + 1f)
        compose.onNodeWithText("Next page").performScrollTo().assertHeightIsAtLeast(48.dp)
    }
}
