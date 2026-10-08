package dev.mobilefoundry.ui.components.feedback.validationchecklist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.mobilefoundry.ui.theme.FoundryTheme

data class ValidationItem(val id: String, val title: String, val satisfied: Boolean, val stateDescription: String)

/** Passive supplied requirements. No validation rules or checkbox actions live here. */
@Composable
fun ValidationChecklist(items: List<ValidationItem>, modifier: Modifier = Modifier) {
    require(items.map { it.id }.distinct().size == items.size)
    val t = FoundryTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
        items.forEach { item -> key(item.id) {
            Row(Modifier.semantics(mergeDescendants = true) { stateDescription = item.stateDescription },
                horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
                Text(if (item.satisfied) "✓" else "○", Modifier.clearAndSetSemantics {},
                    color = if (item.satisfied) t.colors.accent.color else t.colors.inkSecondary.color)
                Text(item.title, Modifier.weight(1f))
            }
        } }
    }
}
