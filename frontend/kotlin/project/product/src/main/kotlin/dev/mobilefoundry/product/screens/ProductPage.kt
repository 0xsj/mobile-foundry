package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Page presentation is product-owned; each feature can replace this placeholder. */
@Composable
fun ProductPage(title: String, subtitle: String, icon: Int, content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page),
        verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        SectionHeader(title, subtitle)
        Card(role = SurfaceRole.FLOATING) {
            Icon(painterResource(icon), contentDescription = null, tint = t.colors.accent.color, modifier = Modifier.size(40.dp))
            Text("Make this space your own.", style = t.typography.heading)
            content()
        }
    }
}
