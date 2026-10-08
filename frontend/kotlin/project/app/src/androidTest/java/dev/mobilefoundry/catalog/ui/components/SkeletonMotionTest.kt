package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.feedback.skeleton.Skeleton
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SkeletonMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualPulsePixelsStopWhenReducedMotionIsSelected() {
        compose.mainClock.autoAdvance = false
        var reduced by mutableStateOf(false)
        compose.setContent {
            FoundryTheme(reduceMotion = reduced) {
                Box(Modifier.size(96.dp, 24.dp).background(Color.White).testTag("loading block")) {
                    Skeleton(height = 24.dp, width = 96.dp)
                }
            }
        }
        fun centerPixel(): Color {
            val pixels = compose.onNodeWithTag("loading block").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        compose.mainClock.advanceTimeByFrame()
        val initial = centerPixel()
        compose.mainClock.advanceTimeBy(450)
        assertNotEquals("Pulse changes actual native pixels", initial, centerPixel())
        compose.runOnIdle { reduced = true }
        compose.mainClock.advanceTimeByFrame()
        val stopped = centerPixel()
        compose.mainClock.advanceTimeBy(1200)
        assertEquals("Reduced motion leaves a static placeholder", stopped, centerPixel())
    }
}
