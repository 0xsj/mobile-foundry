package dev.mobilefoundry.ui.components.patterns.sharelinkcard

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Supplied selectable link or unavailable copy and independent slots. No URL creation, copying or authorization. */
@Composable
fun ShareLinkCard(title: String, link: String?, unavailableLabel: String, modifier: Modifier = Modifier, detail: String? = null,
    status: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Card(modifier = modifier) {
        Text(title, Modifier.semantics { heading() }, style = t.typography.heading)
        if (link != null) SelectionContainer { Text(link, style = t.typography.code, color = t.colors.ink.color) }
        else Text(unavailableLabel, style = t.typography.body, color = t.colors.inkSecondary.color)
        if (detail != null) Text(detail, style = t.typography.caption, color = t.colors.inkSecondary.color)
        status(); actions()
    }
}
