package dev.mobilefoundry.ui.components.display.emptystate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier,
               artwork: @Composable () -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth().padding(t.space.section), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Box(Modifier.clearAndSetSemantics {}) { artwork() }
        Text(title, style = t.typography.heading, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(message, color = t.colors.inkSecondary.color, textAlign = TextAlign.Center)
        actions()
    }
}
