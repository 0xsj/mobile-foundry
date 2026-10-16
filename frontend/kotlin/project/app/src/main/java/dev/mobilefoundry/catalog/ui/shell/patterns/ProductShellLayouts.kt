package dev.mobilefoundry.catalog.ui.shell.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.layout.divider.DividerAxis
import dev.mobilefoundry.ui.components.layout.divider.SectionDivider
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.layout.splitpane.PaneMode
import dev.mobilefoundry.ui.components.layout.splitpane.SplitPane
import dev.mobilefoundry.ui.components.navigation.stepindicator.*
import dev.mobilefoundry.ui.components.navigation.tabbar.TabBar
import dev.mobilefoundry.ui.components.navigation.tabbar.TabItem
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.shells.authshell.AuthShell
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable fun ProductShellLayout(pattern: ProductShellPattern, values: ProductShellState, onAction: (ProductShellAction) -> Unit, drafts: ProductShellDrafts) {
    when (pattern) {
        ProductShellPattern.DAILY -> DailyProductShell(values, onAction)
        ProductShellPattern.WORKSPACE -> WorkspaceProductShell(values, onAction)
        ProductShellPattern.BROWSE -> BrowseProductShell(values, onAction, drafts)
        ProductShellPattern.CANVAS -> CanvasProductShell(values, onAction, drafts)
        ProductShellPattern.SETUP -> SetupProductShell(values, onAction, drafts)
    }
}
@Composable private fun DailyProductShell(values: ProductShellState, onAction: (ProductShellAction) -> Unit) {
    val t = FoundryTheme.tokens
    val icons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_library, R.drawable.ic_tab_studio, R.drawable.ic_tab_account)
    Column(Modifier.fillMaxSize()) {
        VerticalStack(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
            val page = values.dailyPage
            SectionHeader(if (page == "Today") "A little momentum" else "Your ${page.lowercase()}", "A personal space for the things that matter.")
            Card(role = SurfaceRole.FLOATING) {
                ShellArtwork(icons[ProductShellState.dailyPages.indexOf(page)], page, Modifier.height(150.dp))
                Text(if (page == "Today") "Make room for a small win." else "Pick up where you left off.", style = t.typography.heading)
                Text("$page check-ins: ${values.checkIns[page] ?: 0}", style = t.typography.body)
                ActionButton({ onAction(ProductShellAction.CheckIn) }) { Text("Check in") }
            }
            Card {
                SectionHeader("At a glance")
                Text("Saved for later · 03", style = t.typography.label)
                Text("A few things worth returning to.", style = t.typography.caption)
                SectionDivider()
                Text("This week · 07", style = t.typography.label)
                Text("Small steps add up.", style = t.typography.caption)
            }
        }
        TabBar(ProductShellState.dailyPages.mapIndexed { index, page -> TabItem(page, page) { Icon(painterResource(icons[index]), null) } },
            values.dailyPage, { onAction(ProductShellAction.Daily(it)) }, Modifier.padding(horizontal = t.space.page, vertical = t.space.stack))
    }
}
@Composable fun WorkspaceProductShell(values: ProductShellState, onAction: (ProductShellAction) -> Unit) {
    // The width budget includes the primitive's token gutter: 280 + 420 = 700.
    SplitPane(values.workspaceDetail, Modifier.fillMaxSize(), primaryWidth = 280.dp,
        minimumDetailWidth = 420.dp - FoundryTheme.tokens.space.inline,
        primary = { WorkspaceList(values, onAction, Modifier.testTag("workspace-list-pane")) }, detail = { mode ->
            BackHandler(enabled = mode == PaneMode.SINGLE) { onAction(ProductShellAction.WorkspaceBack) }
            WorkspaceDetail(values, onAction, mode == PaneMode.SINGLE, Modifier.testTag("workspace-detail-pane"))
        })
}
@Composable private fun WorkspaceList(values: ProductShellState, onAction: (ProductShellAction) -> Unit, modifier: Modifier) {
    val t = FoundryTheme.tokens
    VerticalStack(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
        SectionHeader("Workspace", "A clear place for work in progress.")
        Card(role = SurfaceRole.FLOATING) { Text("3 projects", style = t.typography.heading); Text("${values.completedItems.size} completed", style = t.typography.caption) }
        ShellSampleItem.workspace.forEach { item -> key(item.id) { Card {
            ActionButton({ onAction(ProductShellAction.Workspace(item.id)) }, variant = if (values.workspaceItem == item.id) ButtonVariant.SECONDARY else ButtonVariant.QUIET) { Text(item.title) }
            Text(item.subtitle, style = t.typography.caption, color = t.colors.inkSecondary.color)
            if (item.id in values.completedItems) Text("Completed", style = t.typography.caption)
        } } }
    }
}
@Composable private fun WorkspaceDetail(values: ProductShellState, onAction: (ProductShellAction) -> Unit, compact: Boolean, modifier: Modifier) {
    val t = FoundryTheme.tokens
    val item = ShellSampleItem.workspace.first { it.id == values.workspaceItem }
    DetailShell(modifier, header = {
        Column(Modifier.padding(t.space.page)) {
            if (compact) ActionButton({ onAction(ProductShellAction.WorkspaceBack) }, variant = ButtonVariant.QUIET) { Text("Back to projects") }
            SectionHeader(item.title, "Project overview")
        }
    }, content = {
        VerticalStack(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
            Card(role = SurfaceRole.FLOATING) {
                ShellArtwork(R.drawable.ic_tab_studio, "WORK IN PROGRESS", Modifier.height(170.dp))
                Text(item.subtitle, style = t.typography.heading)
                Text(if (item.id in values.completedItems) "Project status: complete" else "Project status: in progress", style = t.typography.body)
            }
            Card { SectionHeader("Next step"); Text("Review the idea, choose one useful action, and keep the scope small.", style = t.typography.body) }
        }
    }, actions = { Card(Modifier.padding(t.space.page), role = SurfaceRole.FLOATING) {
        ActionButton({ onAction(ProductShellAction.ToggleDone) }) { Text(if (item.id in values.completedItems) "Reopen project" else "Complete project") }
    } })
}
@Composable private fun BrowseProductShell(values: ProductShellState, onAction: (ProductShellAction) -> Unit, drafts: ProductShellDrafts) {
    val t = FoundryTheme.tokens
    val item = ShellSampleItem.collections.firstOrNull { it.id == values.browseItem }
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = item != null) { onAction(ProductShellAction.Browse(null)) }
    if (item != null) DetailShell(header = { Column(Modifier.padding(t.space.page)) {
        ActionButton({ onAction(ProductShellAction.Browse(null)) }, variant = ButtonVariant.QUIET) { Text("Back to collections") }
        SectionHeader(item.title, "Collection")
    } }, content = {
        VerticalStack(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
            Card(role = SurfaceRole.FLOATING) { ShellArtwork(R.drawable.ic_tab_library, item.title.uppercase(), Modifier.height(240.dp)) }
            Card { Text(item.subtitle, style = t.typography.heading); Text("A place to browse, save and return. Adapt this structure to a shop, directory or media library.", style = t.typography.body) }
        }
    }, actions = { Card(Modifier.padding(t.space.page), role = SurfaceRole.FLOATING) {
        Text(if (item.id in values.savedItems) "Collection saved in this preview." else "Keep this collection close.", style = t.typography.caption)
        ActionButton({ onAction(ProductShellAction.ToggleSaved) }) { Text(if (item.id in values.savedItems) "Unsave collection" else "Save collection") }
    } })
    else VerticalStack(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
        SectionHeader("Discover something good", "Collections for curious days.")
        LabeledTextField("Find collections", drafts.browse, Modifier.fillMaxWidth())
        Text("Saved collections: ${values.savedItems.size}", style = t.typography.caption)
        val query = drafts.browse.text.toString()
        val items = ShellSampleItem.collections.filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
        if (items.isEmpty()) Text("No collections match your search.", style = t.typography.body)
        items.forEach { collection -> key(collection.id) { Card(role = SurfaceRole.FLOATING) {
            ShellArtwork(R.drawable.ic_tab_library, collection.title, Modifier.height(110.dp))
            ActionButton({ focus.clearFocus(); keyboard?.hide(); onAction(ProductShellAction.Browse(collection.id)) }, variant = ButtonVariant.QUIET) { Text("Open ${collection.title}") }
            Text(collection.subtitle, style = t.typography.caption)
        } } }
    }
}
@Composable private fun CanvasProductShell(values: ProductShellState, onAction: (ProductShellAction) -> Unit, drafts: ProductShellDrafts) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    fun act(action: ProductShellAction) { focus.clearFocus(); keyboard?.hide(); onAction(action) }
    DetailShell(header = { SectionHeader("Canvas studio", "A focused space to make something.", Modifier.padding(t.space.page)) }, content = {
        VerticalStack(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
            Card(role = SurfaceRole.FLOATING) {
                ShellCanvasArtwork(values.layers, values.canvasTool, Modifier.height(250.dp))
                Text("Canvas layers: ${values.layers}", style = t.typography.caption)
            }
            Card {
                LabeledTextField("Canvas title", drafts.canvas, Modifier.fillMaxWidth())
                SelectField("Canvas tool", ProductShellState.tools, values.canvasTool, { onAction(ProductShellAction.Tool(it)) }, label = { it })
                Text("Tool: ${values.canvasTool}", style = t.typography.caption)
                Text("Preview exports: ${values.exports}", style = t.typography.caption)
            }
        }
    }, actions = { Card(Modifier.padding(t.space.page), role = SurfaceRole.FLOATING) {
        ActionButton({ act(ProductShellAction.AddLayer) }, variant = ButtonVariant.SECONDARY, enabled = values.layers < 8) { Text("Add layer") }
        ActionButton({ act(ProductShellAction.Export) }) { Text("Preview export") }
    } })
}
@Composable private fun SetupProductShell(values: ProductShellState, onAction: (ProductShellAction) -> Unit, drafts: ProductShellDrafts) {
    val t = FoundryTheme.tokens; val steps = listOf("Welcome", "Your focus", "Review")
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    fun act(action: ProductShellAction) { focus.clearFocus(); keyboard?.hide(); onAction(action) }
    BackHandler(enabled = !values.setupFinished && values.setupStep > 0) { act(ProductShellAction.PreviousStep) }
    DetailShell(header = { SectionHeader("Make it yours", "A guided beginning.", Modifier.padding(t.space.page)) }, content = {
        AuthShell(Modifier.fillMaxSize().padding(t.space.page), header = {
            StepIndicator(if (values.setupFinished) "Setup complete" else "Step ${values.setupStep + 1} of 3", steps.mapIndexed { index, title ->
                val status = if (values.setupFinished || index < values.setupStep) StepStatus.COMPLETED else if (index == values.setupStep) StepStatus.CURRENT else StepStatus.UPCOMING
                StepItem(title, title, status, if (status == StepStatus.COMPLETED) "Completed" else if (status == StepStatus.CURRENT) "Current" else "Upcoming")
            })
        }, content = { Card(role = SurfaceRole.FLOATING) {
            if (values.setupFinished) {
                Text("You're ready to begin", style = t.typography.heading)
                Text("${drafts.name.text.toString().ifEmpty { "Your space" }} · ${values.setupFocus}", style = t.typography.body)
            } else when (values.setupStep) {
                0 -> { SectionHeader("Welcome"); LabeledTextField("Your name", drafts.name, Modifier.fillMaxWidth(), help = "Optional in this preview.") }
                1 -> { SectionHeader("Choose your focus"); SelectField("Your focus", ProductShellState.focuses, values.setupFocus, { onAction(ProductShellAction.Focus(it)) }, label = { it }) }
                else -> { SectionHeader("A good place to start"); Text("Name: ${drafts.name.text.toString().ifEmpty { "Your space" }}"); Text("Focus: ${values.setupFocus}") }
            }
        } }, footer = { Text("Your choices stay in this local preview.", style = t.typography.caption) })
    }, actions = { Card(Modifier.padding(t.space.page), role = SurfaceRole.FLOATING) {
        if (values.setupFinished) ActionButton({ act(ProductShellAction.ResetSetup) }, variant = ButtonVariant.SECONDARY) { Text("Start again") }
        else {
            if (values.setupStep > 0) ActionButton({ act(ProductShellAction.PreviousStep) }, variant = ButtonVariant.QUIET) { Text("Previous step") }
            ActionButton({ act(ProductShellAction.NextStep) }) { Text(if (values.setupStep == 2) "Finish setup" else "Continue setup") }
        }
    } })
}
@Composable private fun ShellArtwork(icon: Int, caption: String, modifier: Modifier) {
    val t = FoundryTheme.tokens
    Box(modifier.fillMaxWidth().background(Brush.linearGradient(listOf(t.colors.accentTint.color, t.colors.surfaceGround.color)), RoundedCornerShape(20.dp))
        .clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
            Icon(painterResource(icon), null, Modifier.size(44.dp), tint = t.colors.accent.color)
            Text(caption, style = t.typography.caption, color = t.colors.inkSecondary.color)
        }
    }
}
@Composable private fun ShellCanvasArtwork(layers: Int, tool: String, modifier: Modifier) {
    val t = FoundryTheme.tokens
    Canvas(modifier.fillMaxWidth().background(t.colors.surfaceGround.color, RoundedCornerShape(20.dp)).semantics { contentDescription = "Canvas preview with $layers layers" }) {
        repeat(layers) { index ->
            rotate(if (tool == "Arrange") index * 12f else -12f) {
                drawRoundRect(t.colors.accent.color.copy(alpha = 0.15f + (index % 3) * 0.12f),
                    Offset(size.width / 4 + (index % 3 - 1) * 25.dp.toPx(), size.height / 2 - 45.dp.toPx() + (index % 4 - 2) * 28.dp.toPx()),
                    Size(size.width / 2, 90.dp.toPx()), CornerRadius(if (tool == "Draw") 60.dp.toPx() else 18.dp.toPx()))
            }
        }
    }
}
