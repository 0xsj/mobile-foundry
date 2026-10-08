package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import java.util.Collections

/** Logical dp on a four-point rhythm, with a two-point optical step. */
@Immutable
object FoundrySpace {
    val steps = Collections.unmodifiableList(listOf(2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 96).map { it.dp })
    val inline get() = steps[2]
    val stack get() = steps[3]
    val section get() = steps[6]
    val page get() = steps[5]
}
