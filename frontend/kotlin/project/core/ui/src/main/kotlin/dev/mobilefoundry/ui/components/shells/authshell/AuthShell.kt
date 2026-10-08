package dev.mobilefoundry.ui.components.shells.authshell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.theme.FoundryTheme

/** A readable, scrolling account-form layout. This shell does not implement authentication. */
@Composable
fun AuthShell(modifier: Modifier = Modifier, maximumWidth: Dp = 480.dp,
    header: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit, footer: @Composable () -> Unit) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentContainer(maximumWidth = maximumWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) { header(); content(); footer() }
        }
    }
}
