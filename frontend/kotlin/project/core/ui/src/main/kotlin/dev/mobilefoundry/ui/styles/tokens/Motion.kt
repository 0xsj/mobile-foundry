package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import java.util.Collections

@Immutable
data class FoundryMotion(val reduced: Boolean) {
    val milliseconds: List<Int> = Collections.unmodifiableList(if (reduced) listOf(0, 0, 0) else listOf(120, 200, 280))
    val standardEasing get() = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
