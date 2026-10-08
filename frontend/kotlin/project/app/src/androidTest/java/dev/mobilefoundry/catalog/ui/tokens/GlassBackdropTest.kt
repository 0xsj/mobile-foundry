package dev.mobilefoundry.catalog.ui.tokens

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.surface.*
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class GlassBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassSamplesTranslatedBackdropAndTracksSceneChangesWhileControlsStayInteractive() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val changed = mutableStateOf(false)
        val reduced = mutableStateOf(false)
        compose.setContent {
            FoundryTheme(style = FoundryThemeStyle.GLASS, reduceTransparency = reduced.value) {
                FoundryBackdrop(Modifier.size(240.dp), background = {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(if (changed.value) Color.Green else Color.Red)
                        if (!changed.value) drawRect(Color.Blue, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
                    }
                }) {
                    FoundrySurface(Modifier.align(Alignment.BottomEnd).size(width = 160.dp, height = 80.dp).testTag("glass"), FoundrySurfaceRole.FLOATING) {
                        var count by remember { mutableIntStateOf(0) }
                        TextButton(onClick = { count++ }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("Pick $count") }
                    }
                }
            }
        }
        fun sample(): Color {
            val pixels = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 4]
        }
        // The floating surface is over the BLUE half, not the RED source origin.
        val blue = sample()
        assertTrue("Expected blue backdrop, got $blue", blue.blue > blue.red + .04f)
        val edge = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        // The source's hard red/blue edge falls one quarter of the way into this translated panel.
        // A plain translucent fill retains the .12 channel step; blur smooths nearby samples.
        val left = edge[edge.width / 4 - 2, edge.height / 4]
        val right = edge[edge.width / 4 + 2, edge.height / 4]
        assertTrue("Expected softened source edge: $left / $right", abs(left.blue - right.blue) < .04f)
        compose.onNodeWithText("Pick 0").performClick()
        compose.onNodeWithText("Pick 1").assertExists()
        compose.runOnIdle { changed.value = true }
        val green = sample()
        assertTrue("Backdrop should update, got $green", green.green > green.blue + .04f)
        compose.runOnIdle { reduced.value = true }
        val solid = sample()
        assertTrue("Opaque fallback must stop sampling the green scene", solid.green - solid.blue < .04f)
        compose.onNodeWithText("Pick 1").performClick()
        compose.onNodeWithText("Pick 2").assertExists()
    }
}
