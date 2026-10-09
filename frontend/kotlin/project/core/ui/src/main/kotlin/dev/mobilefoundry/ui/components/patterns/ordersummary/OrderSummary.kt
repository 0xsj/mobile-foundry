package dev.mobilefoundry.ui.components.patterns.ordersummary

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Caller-formatted order composition. Calculation, disclosures and command eligibility belong to the feature. */
@Composable
fun OrderSummary(title: String, totalTitle: String, totalValue: String, modifier: Modifier = Modifier, subtitle: String? = null,
    totalDetail: String? = null, lines: @Composable () -> Unit, footer: @Composable () -> Unit = {}) {
    Card(modifier = modifier) {
        SectionHeader(title, subtitle = subtitle)
        lines()
        HorizontalDivider(Modifier.clearAndSetSemantics {}, color = FoundryTheme.tokens.colors.line.color)
        KeyValueRow(totalTitle, totalValue, detail = totalDetail)
        footer()
    }
}
