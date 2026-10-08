package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import java.util.Collections

@Immutable
object FoundryShape {
    val radii = Collections.unmodifiableList(listOf(4, 8, 12, 16).map { it.dp })
    val panel get() = radii[3]
    val pill = 999.dp
    /** A lower bound, never a fixed text/control height. */
    val minimumInteractive = 48.dp
}
