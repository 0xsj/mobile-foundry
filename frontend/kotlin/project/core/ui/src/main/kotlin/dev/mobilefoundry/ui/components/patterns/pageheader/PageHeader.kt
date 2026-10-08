package dev.mobilefoundry.ui.components.patterns.pageheader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier,
               actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Text(title, style = t.typography.title, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, color = t.colors.inkSecondary.color)
        actions()
    }
}
