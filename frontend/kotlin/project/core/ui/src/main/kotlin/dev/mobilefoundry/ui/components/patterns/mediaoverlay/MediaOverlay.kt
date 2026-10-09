package dev.mobilefoundry.ui.components.patterns.mediaoverlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Decorative artwork and bottom scrim behind independent overlay content. Put media meaning in the overlay.
 * The host supplies clipping/bounds and readable action surfaces. Artwork must not contain controls. */
@Composable
fun MediaOverlay(modifier: Modifier = Modifier, scrimOpacity: Float = 0.75f,
    artwork: @Composable BoxScope.() -> Unit, overlay: @Composable ColumnScope.() -> Unit) {
    require(scrimOpacity.isFinite() && scrimOpacity in 0f..1f)
    Box(modifier) {
        Box(Modifier.matchParentSize().clearAndSetSemantics {}, content = artwork)
        Box(Modifier.matchParentSize().clearAndSetSemantics {}.background(Brush.verticalGradient(
            0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = scrimOpacity))))
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(FoundryTheme.tokens.space.stack), content = overlay)
        }
    }
}
