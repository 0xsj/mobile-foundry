package dev.mobilefoundry.ui.components.patterns.onboardingpage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.components.patterns.pageheader.PageHeader
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One bounded page with scrolling artwork/copy/content and separate actions.
 * The caller owns step identity, validation, focus and transitions. */
@Composable
fun OnboardingPage(title: String, message: String, modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit, actions: @Composable () -> Unit) {
    val t = FoundryTheme.tokens
    DetailShell(modifier, header = {}, content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
                    artwork(); PageHeader(title, message); content()
                }
            }
        }
    }, actions = { ContentContainer { actions() } })
}
