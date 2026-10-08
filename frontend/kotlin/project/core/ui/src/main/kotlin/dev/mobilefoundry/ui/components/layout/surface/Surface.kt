package dev.mobilefoundry.ui.components.layout.surface

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.styles.tokens.FoundrySurfaceMaterial
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class SurfaceRole { CONTENT, FLOATING }

/** Padding and interactions belong to the caller. Content panels always stay opaque. */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    role: SurfaceRole = SurfaceRole.CONTENT,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = FoundryTheme.tokens
    val source = LocalBackdropSource.current
    val glass = role == SurfaceRole.FLOATING && tokens.materials.floatingWithBackdrop(
        Build.VERSION.SDK_INT >= 31, source != null) == FoundrySurfaceMaterial.GLASS
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(tokens.shape.panel)
    val opaque = if (role == SurfaceRole.FLOATING) tokens.colors.surfaceRaised.color else tokens.colors.surfacePanel.color
    // High-opacity neutral tint bounds text contrast while still showing a blurred scene.
    val tint = opaque.copy(alpha = tokens.materials.floatingTintAlpha(tokens.appearance))
    val edge = if (glass) Brush.linearGradient(listOf(Color.White.copy(alpha = tokens.materials.edgeHighlightAlpha), tokens.colors.lineStrong.color))
        else Brush.linearGradient(listOf(tokens.colors.line.color, tokens.colors.line.color))
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }.clip(shape)
        .drawWithContent {
            if (glass && source != null) {
                val offset = source.origin - origin
                translate(offset.x, offset.y) { drawLayer(source.blurred) }
                drawRect(tint)
            } else {
                drawRect(opaque)
            }
            drawContent()
        }.border(1.dp, edge, shape)) {
        CompositionLocalProvider(LocalContentColor provides tokens.colors.ink.color) { content() }
    }
}
