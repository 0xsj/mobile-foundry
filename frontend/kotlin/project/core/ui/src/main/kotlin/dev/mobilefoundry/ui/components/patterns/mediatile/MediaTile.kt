package dev.mobilefoundry.ui.components.patterns.mediatile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.layout.aspectratio.MediaFrame
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Media frame, supplied metadata and independent actions. No implicit tap, fetching or media model. */
@Composable
fun MediaTile(title: String, subtitle: String? = null, modifier: Modifier = Modifier, ratio: Float = 4f / 3f,
    artwork: @Composable BoxScope.() -> Unit, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Card(modifier) {
        MediaFrame(ratio = ratio, content = artwork)
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(title, style = t.typography.label)
            subtitle?.let { Text(it, color = t.colors.inkSecondary.color) }
        }
        actions()
    }
}
