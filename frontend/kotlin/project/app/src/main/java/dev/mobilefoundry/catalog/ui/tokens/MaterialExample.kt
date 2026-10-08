package dev.mobilefoundry.catalog.ui.tokens

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.surface.*
import dev.mobilefoundry.ui.styles.tokens.FoundrySurfaceMaterial
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun MaterialExample() {
    val tokens = FoundryTheme.tokens
    val c = tokens.colors
    var moved by rememberSaveable { mutableStateOf(false) }
    var selections by rememberSaveable { mutableIntStateOf(0) }
    val floating = tokens.materials.floatingWithBackdrop(Build.VERSION.SDK_INT >= 31, hasBackdrop = true)
    Column(verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        Text("Floating surfaces", style = tokens.typography.heading)
        val styleName = if (tokens.materials.style == FoundryThemeStyle.GLASS) "Glass" else "Solid"
        val floatingName = if (floating == FoundrySurfaceMaterial.GLASS) "Glass" else "Solid"
        Text("Theme: $styleName · Floating: $floatingName", style = tokens.typography.caption)
        Backdrop(Modifier.fillMaxWidth().heightIn(min = 300.dp).clip(RoundedCornerShape(tokens.shape.panel)),
            background = {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(c.surfaceSunk.color, c.accentTint.color)))
                    val center = Offset(size.width * if (moved) .60f else .35f, size.height * .4f)
                    rotate(if (moved) 30f else -20f, center) {
                        drawRoundRect(c.accent.color, center - Offset(size.width * .28f, 90.dp.toPx()),
                            Size(size.width * .56f, 180.dp.toPx()), CornerRadius(32.dp.toPx()))
                    }
                    drawCircle(c.info.color, 50.dp.toPx(), Offset(size.width * .8f, size.height * .4f))
                }
            }) {
            Column(Modifier.fillMaxWidth().padding(top = 140.dp).align(Alignment.BottomCenter).padding(tokens.space.stack)) {
                Surface(role = SurfaceRole.FLOATING) {
                    Column(Modifier.fillMaxWidth().padding(tokens.space.stack), verticalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        Text("Scene controls", style = tokens.typography.label)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                            TextButton(onClick = { moved = !moved }) { Text("Move scene", color = c.ink.color) }
                            TextButton(onClick = { selections++ }) { Text("Select object", color = c.ink.color) }
                        }
                        Text("Selections: $selections · Scene: ${if (moved) "B" else "A"}", style = tokens.typography.caption)
                    }
                }
            }
        }
        Text("Move the scene to inspect the material. Content panels below stay opaque.",
            style = tokens.typography.caption, color = c.inkSecondary.color)
    }
}
