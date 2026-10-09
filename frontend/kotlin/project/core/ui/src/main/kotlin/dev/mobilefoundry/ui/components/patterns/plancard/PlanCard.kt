package dev.mobilefoundry.ui.components.patterns.plancard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Structured supplied plan copy and one native choice action. Slots are outside the choice target, not its label. */
@Composable
fun PlanCard(title: String, selected: Boolean, actionLabel: String, onSelect: () -> Unit,
    modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true,
    price: @Composable () -> Unit, status: @Composable ColumnScope.() -> Unit = {}, features: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Card(modifier.border(if (selected) 2.dp else 1.dp, if (selected) t.colors.accent.color else t.colors.lineStrong.color, RoundedCornerShape(t.shape.panel))) {
        SectionHeader(title, subtitle = subtitle)
        status()
        price()
        HorizontalDivider(color = t.colors.line.color)
        features()
        ActionButton({ if (enabled && !selected) onSelect() }, Modifier.semantics { this.selected = selected },
            variant = if (selected) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY, enabled = enabled && !selected) { Text(actionLabel) }
    }
}
