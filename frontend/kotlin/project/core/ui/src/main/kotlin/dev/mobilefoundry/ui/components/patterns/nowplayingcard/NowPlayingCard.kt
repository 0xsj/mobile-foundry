package dev.mobilefoundry.ui.components.patterns.nowplayingcard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.layout.aspectratio.MediaFrame
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive supplied media identity/artwork and independent slots. No engine, media loading or clock ownership. */
@Composable
fun NowPlayingCard(title: String, accessibilityLabel: String, modifier: Modifier = Modifier, detail: String? = null, artworkSize: Dp = 80.dp,
    artwork: @Composable BoxScope.() -> Unit = {}, timeline: @Composable ColumnScope.() -> Unit = {},
    controls: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    require(artworkSize.value.isFinite() && artworkSize > 0.dp)
    val t = FoundryTheme.tokens
    val cover: @Composable () -> Unit = { MediaFrame(Modifier.size(artworkSize).clip(RoundedCornerShape(t.shape.radii[1])), ratio = 1f, content = artwork) }
    val identity: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(title, style = t.typography.heading, color = t.colors.ink.color)
            if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
        }
    }
    Card(modifier) {
        val meaning = Modifier.clearAndSetSemantics { contentDescription = accessibilityLabel; heading() }
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(meaning, verticalArrangement = Arrangement.spacedBy(t.space.inline)) { cover(); identity() }
        } else Row(meaning, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) { cover(); Column(Modifier.weight(1f)) { identity() } }
        timeline(); controls(); actions()
    }
}
