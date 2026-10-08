package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.feedback.skeleton.Skeleton
import dev.mobilefoundry.ui.components.feedback.skeleton.SkeletonShape
import dev.mobilefoundry.ui.components.feedback.toast.ToastAction
import dev.mobilefoundry.ui.components.feedback.toast.ToastBanner
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
internal fun FeedbackExamples(loading: Boolean, onLoading: (Boolean) -> Unit, reduced: Boolean, onReduced: (Boolean) -> Unit,
    notice: Int, onNotice: (Int) -> Unit, undos: Int, retries: Int, onUndo: () -> Unit, onRetry: () -> Unit) {
    val t = FoundryTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card {
            ToggleField("Show loading placeholders", loading, onLoading)
            ToggleField("Reduce loading motion", reduced, onReduced)
            if (loading) {
                Text("Loading collection…", style = t.typography.label)
                FoundryTheme(reduceMotion = reduced) {
                    Row(horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
                        Skeleton(height = 48.dp, shape = SkeletonShape.CIRCLE)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                            Skeleton(height = 18.dp)
                            Skeleton(height = 12.dp, width = 140.dp)
                            Skeleton(height = 12.dp, width = 100.dp)
                        }
                    }
                }
                Text(if (reduced || t.motion.reduced) "Static placeholders" else "Pulsing placeholders", style = t.typography.caption)
            } else ListRow("Collection ready", "The host replaces placeholders with real content.")
        }
        Card {
            Text("Transient feedback", style = t.typography.heading)
            Text("These notices stay until you dismiss or replace them. No operation runs automatically.", style = t.typography.caption)
            ActionButton({ onNotice(1) }, variant = ButtonVariant.SECONDARY) { Text("Show saved notice") }
            ActionButton({ onNotice(2) }, variant = ButtonVariant.SECONDARY) { Text("Show retry notice") }
            Text("Undo actions: $undos · Retry actions: $retries", style = t.typography.caption)
        }
        if (notice != 0) ToastBanner(if (notice == 1) "Example saved on this device." else "Example could not be saved.",
            "Dismiss notice", { onNotice(0) }, tone = if (notice == 1) MessageTone.INFO else MessageTone.WARNING,
            action = ToastAction(if (notice == 1) "Undo example" else "Retry example") {
                if (notice == 1) { onUndo(); onNotice(0) } else { onRetry(); onNotice(1) }
            })
    }
}
