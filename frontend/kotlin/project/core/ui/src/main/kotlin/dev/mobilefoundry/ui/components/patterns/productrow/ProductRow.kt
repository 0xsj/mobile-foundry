package dev.mobilefoundry.ui.components.patterns.productrow

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive product copy/artwork and price with independent status/actions. No stock or cart ownership. */
@Composable
fun ProductRow(title: String, modifier: Modifier = Modifier, detail: String? = null, artwork: @Composable () -> Unit = {},
    price: @Composable () -> Unit, status: @Composable () -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FoundryTheme.tokens.space.stack)) {
        ListRow(title, subtitle = detail, leading = { Box(Modifier.clearAndSetSemantics {}) { artwork() } })
        price()
        status()
        actions()
    }
}
