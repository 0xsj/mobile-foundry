package dev.mobilefoundry.ui.components.layout.surface

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import dev.mobilefoundry.ui.styles.tokens.FoundrySurfaceMaterial
import dev.mobilefoundry.ui.theme.FoundryTheme

internal class BackdropSource(val sharp: GraphicsLayer, val blurred: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalBackdropSource = staticCompositionLocalOf<BackdropSource?> { null }

/**
 * Records only the background slot, excluding floating controls from their own source.
 * Use for a bounded Compose scene. External SurfaceView/GL frames need a renderer bridge.
 * Layers are released by rememberGraphicsLayer when the host leaves composition.
 */
@Composable
fun Backdrop(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val sharp = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val source = remember(sharp, blurred) { BackdropSource(sharp, blurred) }
    val materials = FoundryTheme.tokens.materials
    val capture = materials.floating == FoundrySurfaceMaterial.GLASS && Build.VERSION.SDK_INT >= 31
    Box(modifier) {
        Box(Modifier.matchParentSize().onGloballyPositioned { source.origin = it.positionInRoot() }
            .drawWithContent {
                if (capture) {
                    sharp.record { this@drawWithContent.drawContent() }
                    blurred.record { drawLayer(sharp) }
                    val blur = materials.backdropBlur.toPx()
                    blurred.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                    drawLayer(sharp)
                } else {
                    drawContent()
                }
            }, content = background)
        CompositionLocalProvider(LocalBackdropSource provides if (capture) source else null) {
            content()
        }
    }
}
