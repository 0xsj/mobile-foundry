package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.charts.barchart.BarChart
import dev.mobilefoundry.ui.components.charts.barchart.ChartBar
import dev.mobilefoundry.ui.components.charts.legend.LegendItem
import dev.mobilefoundry.ui.components.charts.progressring.ProgressRing
import dev.mobilefoundry.ui.components.charts.sparkline.Sparkline
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.chartpanel.ChartPanel
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InsightsComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeCanvasDrawsExtremeAndFlatSamplesAndClearsEmptyData() {
        var samples by mutableStateOf(listOf(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE))
        compose.setContent { FoundryTheme {
            Box(Modifier.width(220.dp)) { Sparkline(samples, "Supplied sample summary", Modifier.testTag("spark-probe"), color = Color.Red) }
        } }
        fun redPixels(): List<Pair<Int, Int>> {
            val pixels = compose.onNodeWithTag("spark-probe").captureToImage().toPixelMap()
            return buildList { for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.red > .8 && color.green < .2 && color.blue < .2) add(x to y)
            } }
        }
        val bounds = compose.onNodeWithTag("spark-probe").fetchSemanticsNode().boundsInRoot
        val red = redPixels(); assertTrue(red.isNotEmpty())
        assertTrue(red.filter { it.first < bounds.width * .1 }.all { it.second > bounds.height * .7 })
        assertTrue(red.filter { it.first > bounds.width * .9 }.all { it.second < bounds.height * .3 })
        compose.onNodeWithContentDescription("Supplied sample summary").assertExists()
        compose.runOnIdle { samples = listOf(42.0, 42.0, 42.0) }
        val flat = redPixels(); assertTrue(flat.isNotEmpty())
        assertTrue(flat.all { kotlin.math.abs(it.second - bounds.height / 2) < 8 * compose.density.density })
        compose.runOnIdle { samples = emptyList() }
        assertTrue(redPixels().isEmpty())
    }
    @Test fun narrowLargeTextPanelsRetainReadableDataProgressAndIndependentActions() {
        var enabled by mutableStateOf(true)
        var inspections = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FoundryTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        ChartPanel("Longer dashboard title", Modifier.testTag("panel-probe"), plot = {
                            BarChart("Focus by category", listOf(ChartBar("one", "A longer category label", 75.0, "75 minutes")), 100.0)
                            ProgressRing("Completed sessions", 1.7, "20 of 20", Modifier.testTag("ring-probe"))
                        }, legend = { LegendItem("A longer legend with meaningful units") }, footer = {
                            ActionButton({ inspections++ }, Modifier.testTag("inspect-probe"), enabled = enabled) { androidx.compose.material3.Text("Inspect sample values") }
                        })
                    }
                }
            }
        }
        compose.onNode(hasText("A longer category label") and hasText("75 minutes")).assertExists()
        compose.onNodeWithTag("ring-probe").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 of 20"))
        compose.onNodeWithTag("inspect-probe").performScrollTo().performClick()
        assertEquals(1, inspections)
        val panel = compose.onNodeWithTag("panel-probe").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("inspect-probe").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= panel.left && action.right <= panel.right && action.height >= 48 * compose.density.density)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("inspect-probe").assertIsNotEnabled()
    }
}
