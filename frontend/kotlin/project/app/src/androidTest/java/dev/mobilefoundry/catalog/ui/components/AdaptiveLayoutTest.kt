package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.aspectratio.MediaFrame
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.components.layout.grid.AdaptiveGrid
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AdaptiveLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun measuredGridReflowsForWidthAndTextSizePreservingChildStateAndRtlOrder() {
        var width by mutableStateOf(360.dp)
        var fontScale by mutableFloatStateOf(1f)
        var rtl by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FoundryTheme {
                    AdaptiveGrid(Modifier.requiredWidth(width), minimumItemWidth = 100.dp, spacing = 12.dp) {
                        repeat(4) { index -> key(index) {
                            var count by remember { mutableIntStateOf(0) }
                            Column(Modifier.testTag("tile $index")) {
                                TextButton(onClick = { count++ }) { Text("Count $index: $count") }
                                Spacer(Modifier.height(if (index == 1) 100.dp else 20.dp))
                            }
                        } }
                    }
                }
            }
        }
        fun tile(index: Int) = compose.onNodeWithTag("tile $index").fetchSemanticsNode().boundsInRoot
        assertEquals(tile(0).top, tile(1).top, 1f)
        assertTrue(tile(3).top > tile(1).bottom)
        compose.onNodeWithText("Count 0: 0").performClick()
        compose.runOnIdle { width = 220.dp }
        assertEquals(tile(0).top, tile(1).top, 1f)
        assertTrue(tile(2).top > tile(1).bottom)
        compose.runOnIdle { width = 360.dp; fontScale = 2f }
        assertTrue("Large text reduces columns", tile(1).top > tile(0).bottom)
        compose.onNodeWithText("Count 0: 1").assertExists()
        compose.runOnIdle { fontScale = 1f; rtl = true }
        assertTrue("RTL starts at the right", tile(0).left > tile(1).left)
        compose.onNodeWithText("Count 0: 1").assertExists()
    }

    @Test fun readableContainerIncludesInsetsAndMediaKeepsItsRatio() {
        compose.setContent {
            FoundryTheme {
                Box(Modifier.requiredWidth(360.dp).testTag("outer")) {
                    ContentContainer(maximumWidth = 240.dp, contentPadding = PaddingValues(10.dp)) {
                        MediaFrame(Modifier.testTag("media"), ratio = 4f / 3f) { Text("Preview") }
                    }
                }
            }
        }
        val density = compose.density.density
        val outer = compose.onNodeWithTag("outer").fetchSemanticsNode().boundsInRoot
        val media = compose.onNodeWithTag("media").fetchSemanticsNode().boundsInRoot
        assertEquals(220f * density, media.width, 2f)
        assertEquals(4f / 3f, media.width / media.height, 0.01f)
        assertEquals(outer.center.x, media.center.x, 1f)
    }
}
