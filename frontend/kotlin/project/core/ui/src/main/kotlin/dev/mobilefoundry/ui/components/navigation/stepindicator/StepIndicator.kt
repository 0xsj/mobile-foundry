package dev.mobilefoundry.ui.components.navigation.stepindicator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class StepStatus { COMPLETED, CURRENT, UPCOMING }
data class StepItem(val id: String, val title: String, val status: StepStatus, val stateDescription: String)

/** Passive ordered progress. The caller chooses statuses, copy and navigation policy. */
@Composable
fun StepIndicator(summary: String, steps: List<StepItem>, modifier: Modifier = Modifier) {
    require(steps.map { it.id }.distinct().size == steps.size)
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        Text(summary, style = t.typography.caption, color = t.colors.inkSecondary.color)
        steps.forEachIndexed { index, step -> key(step.id) {
            Row(Modifier.semantics(mergeDescendants = true) { stateDescription = step.stateDescription },
                horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
                Text(if (step.status == StepStatus.COMPLETED) "✓" else "${index + 1}",
                    Modifier.background(if (step.status == StepStatus.CURRENT) t.colors.accentTint.color else t.colors.surfacePanel.color,
                        CircleShape).padding(t.space.inline).clearAndSetSemantics {}, style = t.typography.label)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                    Text(step.title, style = t.typography.label)
                    Text(step.stateDescription, Modifier.clearAndSetSemantics {}, style = t.typography.caption, color = t.colors.inkSecondary.color)
                }
            }
        } }
    }
}
