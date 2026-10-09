package dev.mobilefoundry.ui.components.patterns.attachmentrow

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** File presentation with independent actions. No file loading, preview route, or transfer operation. */
@Composable
fun AttachmentRow(title: String, detail: String, modifier: Modifier = Modifier,
    preview: @Composable () -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.inline)) {
        ListRow(title, detail, leading = {
            Box(Modifier.size(48.dp).clipToBounds().clearAndSetSemantics {}) { preview() }
        })
        actions()
    }
}
