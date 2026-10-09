package dev.mobilefoundry.ui.components.patterns.permissioncard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader

/** Rationale, supplied status and independent caller actions. Never queries permissions or opens settings. */
@Composable
fun PermissionCard(title: String, message: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit = {},
    status: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    Card(modifier = modifier) {
        Box(Modifier.clearAndSetSemantics {}) { icon() }
        SectionHeader(title, subtitle = message)
        status(); actions()
    }
}
