package dev.mobilefoundry.ui.components.feedback.skeleton

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class SkeletonShape { ROUNDED, CIRCLE }

/** Decorative loading geometry. The host supplies the real loading label. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, width: Dp? = null,
    shape: SkeletonShape = SkeletonShape.ROUNDED, animated: Boolean = true) {
    require(height.value.isFinite() && height > 0.dp && (width == null || (width.value.isFinite() && width > 0.dp)))
    val t = FoundryTheme.tokens
    val opacity = if (animated && !t.motion.reduced) {
        val transition = rememberInfiniteTransition(label = "Skeleton pulse")
        val pulse by transition.animateFloat(0.45f, 0.85f,
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Skeleton opacity")
        pulse
    } else 1f
    val bounds = if (shape == SkeletonShape.CIRCLE) modifier.size(height)
        else (if (width != null) modifier.width(width) else modifier.fillMaxWidth()).height(height)
    Box(bounds.alpha(opacity).background(t.colors.line.color,
        RoundedCornerShape(if (shape == SkeletonShape.CIRCLE) height / 2 else t.shape.radii[1]))
        .clearAndSetSemantics {})
}
