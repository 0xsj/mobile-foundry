package dev.mobilefoundry.ui.components.patterns.verificationcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Supplied delivery identity/passive artwork with independent slots. No auth, masking or delivery work. */
@Composable
fun VerificationCard(title: String, destination: String, accessibilityLabel: String, modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit = {},
    status: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    val identity: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(title, style = t.typography.heading)
            Text(destination, style = t.typography.body, color = t.colors.inkSecondary.color)
        }
    }
    Card(modifier) {
        val meaning = Modifier.clearAndSetSemantics { contentDescription = accessibilityLabel; heading() }
        if (LocalDensity.current.fontScale >= 1.5f) Column(meaning, verticalArrangement = Arrangement.spacedBy(t.space.inline)) { artwork(); identity() }
        else Row(meaning, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) { artwork(); Column(Modifier.weight(1f)) { identity() } }
        content(); status(); actions()
    }
}
