package dev.mobilefoundry.ui.components.layout.splitpane

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class PaneMode { SINGLE, SPLIT }

/** Bounded viewport only. Slots own scrolling; host owns selection, Back and state that outlives reflow.
 * Primary stays at the logical leading edge. Large text (font scale >= 1.5) prefers one pane. */
@Composable
fun SplitPane(detailPresented: Boolean, modifier: Modifier = Modifier, forceSingle: Boolean = false,
    primaryWidth: Dp = 320.dp, minimumDetailWidth: Dp = 320.dp,
    primary: @Composable (PaneMode) -> Unit, detail: @Composable (PaneMode) -> Unit) {
    require(primaryWidth.value.isFinite() && primaryWidth.value > 0 && minimumDetailWidth.value.isFinite() && minimumDetailWidth.value > 0)
    val gap = FoundryTheme.tokens.space.inline
    val largeText = LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(modifier) {
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) { "SplitPane requires a bounded viewport" }
        if (!forceSingle && !largeText && maxWidth >= primaryWidth + minimumDetailWidth + gap) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.width(primaryWidth).fillMaxHeight()) { primary(PaneMode.SPLIT) }
                Box(Modifier.weight(1f).fillMaxHeight()) { detail(PaneMode.SPLIT) }
            }
        } else {
            Box(Modifier.fillMaxSize()) { if (detailPresented) detail(PaneMode.SINGLE) else primary(PaneMode.SINGLE) }
        }
    }
}
