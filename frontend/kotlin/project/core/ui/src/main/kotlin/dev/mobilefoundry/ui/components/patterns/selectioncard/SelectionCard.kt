package dev.mobilefoundry.ui.components.patterns.selectioncard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Single-choice option. Content is a label and must not include nested controls. */
@Composable
fun SelectionCard(selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier,
                  enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    val shape = RoundedCornerShape(t.shape.panel)
    Card(modifier.clip(shape).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .border(if (selected) 2.dp else 1.dp, if (selected) t.colors.accent.color else t.colors.lineStrong.color, shape)) {
        Row(horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.space.inline), content = content)
            RadioButton(selected, onClick = null, enabled = enabled)
        }
    }
}
