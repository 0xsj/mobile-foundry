package dev.mobilefoundry.ui.components.feedback.progress

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** null and nonfinite input are indeterminate; finite progress is clamped to 0...1. */
@Composable
fun ProgressIndicator(title: String, modifier: Modifier = Modifier, fraction: Float? = null) {
    val t = FoundryTheme.tokens
    val value = fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label)
        val bar = Modifier.fillMaxWidth().semantics { contentDescription = title }
        if (value == null) LinearProgressIndicator(modifier = bar)
        else LinearProgressIndicator(progress = { value }, modifier = bar)
    }
}
