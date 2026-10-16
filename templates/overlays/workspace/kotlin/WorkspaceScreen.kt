package dev.mobilefoundry.product.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.product.ProductProject
import dev.mobilefoundry.product.ProductState
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.layout.splitpane.PaneMode
import dev.mobilefoundry.ui.components.layout.splitpane.SplitPane
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun ProductContent(state: ProductState, onStateChange: (ProductState) -> Unit) {
    val t = FoundryTheme.tokens
    SplitPane(state.detailPresented, Modifier.fillMaxSize(), primaryWidth = 280.dp,
        minimumDetailWidth = 420.dp - t.space.inline, primary = {
            ProjectList(state, onStateChange)
        }, detail = { mode ->
            BackHandler(enabled = mode == PaneMode.SINGLE && state.detailPresented) { onStateChange(state.back()) }
            ProjectDetail(state, onStateChange, compact = mode == PaneMode.SINGLE)
        })
}

@Composable private fun ProjectList(state: ProductState, onStateChange: (ProductState) -> Unit) {
    val t = FoundryTheme.tokens
    VerticalStack(Modifier.fillMaxSize().testTag("workspace-list").verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
        SectionHeader("Projects", "Choose a project to get started.")
        ProductProject.entries.forEach { project -> key(project.id) {
            Card(role = SurfaceRole.FLOATING) {
                ActionButton({ onStateChange(state.select(project.id)) }, modifier = Modifier.testTag(project.id),
                    variant = if (state.selectedProject == project) ButtonVariant.SECONDARY else ButtonVariant.QUIET) { Text(project.title) }
                Text("Replace this placeholder with your project.", style = t.typography.caption)
            }
        } }
    }
}

@Composable private fun ProjectDetail(state: ProductState, onStateChange: (ProductState) -> Unit, compact: Boolean) {
    val t = FoundryTheme.tokens
    val project = state.selectedProject
    if (project == null) {
        Column(Modifier.fillMaxSize().testTag("workspace-empty").verticalScroll(rememberScrollState()).padding(t.space.page)) {
            Card(role = SurfaceRole.FLOATING) { SectionHeader("Select a project", "Its overview will appear here.") }
        }
    } else DetailShell(Modifier.testTag("workspace-detail"), header = {
        Column(Modifier.padding(t.space.page), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            if (compact) ActionButton({ onStateChange(state.back()) }, modifier = Modifier.testTag("workspace-back"), variant = ButtonVariant.QUIET) { Text("Back to projects") }
            SectionHeader(project.title, "Project overview")
        }
    }, content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page)) {
            Card(role = SurfaceRole.FLOATING) {
                Text("Your project starts here.", style = t.typography.heading)
                Text("Add your tools, documents or activity to this screen.", style = t.typography.body)
            }
        }
    }, actions = {})
}
