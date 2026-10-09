package dev.mobilefoundry.ui.components.feedback.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.feedback.progress.ProgressIndicator
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class TransferPhase { WAITING, TRANSFERRING, PAUSED, FAILED, COMPLETE }

/** Projection of an external transfer. Only transferring displays progress; actions never start work here. */
@Composable
fun TransferStatus(title: String, status: String, phase: TransferPhase, modifier: Modifier = Modifier,
    fraction: Float? = null, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        if (phase == TransferPhase.TRANSFERRING) ProgressIndicator(title, fraction = fraction)
        else Text(title, style = t.typography.label)
        Text(status, style = t.typography.caption,
            color = if (phase == TransferPhase.FAILED) t.colors.crit.color else t.colors.inkSecondary.color)
        actions()
    }
}
