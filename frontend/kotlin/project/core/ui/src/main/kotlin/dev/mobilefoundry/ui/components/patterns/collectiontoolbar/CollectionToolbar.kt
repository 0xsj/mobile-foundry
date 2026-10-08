package dev.mobilefoundry.ui.components.patterns.collectiontoolbar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Native slots only; filtering, sorting, selection and summary copy are owned by the caller. */
@Composable
fun CollectionToolbar(title: String, modifier: Modifier = Modifier, summary: String? = null,
    filters: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Text(title, style = t.typography.heading, modifier = Modifier.semantics { heading() })
        summary?.let { Text(it, style = t.typography.caption, color = t.colors.inkSecondary.color) }
        filters()
        actions()
    }
}
