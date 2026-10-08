package dev.mobilefoundry.ui.components.feedback.loadmore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.feedback.progress.ProgressIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class LoadMorePhase { IDLE, LOADING, FAILED, EXHAUSTED }

/** Explicit pagination affordance; no work on appearance and no cursor, task or retry policy. */
@Composable
fun LoadMoreFooter(phase: LoadMorePhase, message: String, actionLabel: String, onLoad: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        if (phase == LoadMorePhase.LOADING) ProgressIndicator(message)
        else {
            Text(message, style = t.typography.caption, color = t.colors.inkSecondary.color)
            if (phase != LoadMorePhase.EXHAUSTED) ActionButton(onLoad, variant = ButtonVariant.SECONDARY, enabled = enabled) { Text(actionLabel) }
        }
    }
}
