package dev.mobilefoundry.ui.components.patterns.playbackcontrols

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout

/** Independent native actions. Playing chooses only the glyph; caller owns meaning, admission and effects. */
@Composable
fun PlaybackControls(isPlaying: Boolean, previousLabel: String, toggleLabel: String, nextLabel: String, stateLabel: String,
    onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier,
    previousEnabled: Boolean = true, toggleEnabled: Boolean = true, nextEnabled: Boolean = true) {
    WrapLayout(modifier) {
        IconAction(previousLabel, onPrevious, enabled = previousEnabled) { PlaybackGlyph(PlaybackMark.PREVIOUS) }
        IconAction(toggleLabel, onToggle, Modifier.semantics { stateDescription = stateLabel }, variant = ButtonVariant.PRIMARY, enabled = toggleEnabled) {
            PlaybackGlyph(if (isPlaying) PlaybackMark.PAUSE else PlaybackMark.PLAY)
        }
        IconAction(nextLabel, onNext, enabled = nextEnabled) { PlaybackGlyph(PlaybackMark.NEXT) }
    }
}
private enum class PlaybackMark { PREVIOUS, PLAY, PAUSE, NEXT }
@Composable
private fun PlaybackGlyph(mark: PlaybackMark) {
    val ink = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        if (mark == PlaybackMark.PAUSE) {
            drawRect(ink, Offset(w * .24f, h * .18f), Size(w * .18f, h * .64f))
            drawRect(ink, Offset(w * .58f, h * .18f), Size(w * .18f, h * .64f))
        } else {
            val previous = mark == PlaybackMark.PREVIOUS
            val path = Path().apply {
                if (previous) { moveTo(w * .76f, h * .18f); lineTo(w * .28f, h * .5f); lineTo(w * .76f, h * .82f) }
                else { moveTo(w * .24f, h * .18f); lineTo(w * .72f, h * .5f); lineTo(w * .24f, h * .82f) }
                close()
            }
            drawPath(path, ink)
            if (mark == PlaybackMark.PREVIOUS || mark == PlaybackMark.NEXT) {
                drawRect(ink, Offset(w * (if (previous) .12f else .80f), h * .18f), Size(w * .08f, h * .64f))
            }
        }
    }
}
