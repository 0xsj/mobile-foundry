package dev.mobilefoundry.ui.components.patterns.sectionheader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Compact section title; supplied actions keep their own semantics and grow vertically. */
@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.heading, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, color = t.colors.inkSecondary.color) }
        actions()
    }
}
