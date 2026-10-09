package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.splitpane.PaneMode
import dev.mobilefoundry.ui.components.layout.splitpane.SplitPane
import dev.mobilefoundry.ui.components.layout.surface.Backdrop
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.breadcrumbs.BreadcrumbItem
import dev.mobilefoundry.ui.components.navigation.breadcrumbs.BreadcrumbTrail
import dev.mobilefoundry.ui.components.navigation.navigationrail.DestinationRail
import dev.mobilefoundry.ui.components.navigation.navigationrail.RailDestination
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.navigation.tabs.Tabs
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.patterns.selectioncard.SelectionCard
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class WorkspaceDestination(val id: String, val label: String) { ALL("all", "All projects"), STARRED("starred", "Starred"), ARCHIVED("archived", "Archived") }
internal data class WorkspaceProject(val id: String, val title: String, val detail: String, val archived: Boolean = false) {
    companion object { val all = listOf(
        WorkspaceProject("atlas", "Atlas workspace", "A place to collect a new idea and shape its first version."),
        WorkspaceProject("orbit", "Orbit study", "A small study of motion, materials and interaction."),
        WorkspaceProject("field", "Field notes", "An archived collection of observations from everyday work.", true)) }
}
internal data class WorkspaceValues(val destination: WorkspaceDestination = WorkspaceDestination.ALL, val selected: String? = null,
    val detailPresented: Boolean = false, val starred: List<String> = listOf("atlas"), val forceSingle: Boolean = false) {
    val projects get() = WorkspaceProject.all.filter { when (destination) {
        WorkspaceDestination.ALL -> !it.archived
        WorkspaceDestination.STARRED -> it.id in starred && !it.archived
        WorkspaceDestination.ARCHIVED -> it.archived
    } }
    val project get() = projects.firstOrNull { it.id == selected }
    val destinations get() = WorkspaceDestination.entries.map { RailDestination(it.id, it.label) } + RailDestination("shared", "Shared", enabled = false)
    val breadcrumbs get() = listOf(BreadcrumbItem("workspace", "Workspace"), BreadcrumbItem("collection", destination.label)) +
        (if (detailPresented) project?.let { listOf(BreadcrumbItem("project:${it.id}", it.title)) } ?: emptyList() else emptyList())
    fun navigate(id: String) = WorkspaceDestination.entries.firstOrNull { it.id == id }?.let { copy(destination = it, detailPresented = false) } ?: this
    fun open(id: String) = if (projects.any { it.id == id }) copy(selected = id, detailPresented = true) else this
    fun breadcrumb(id: String) = when (id) {
        "workspace" -> copy(destination = WorkspaceDestination.ALL, detailPresented = false)
        "collection" -> copy(detailPresented = false)
        else -> this
    }
    fun setStarred(value: Boolean, id: String): WorkspaceValues {
        if (WorkspaceProject.all.none { it.id == id }) return this
        return copy(starred = if (value) (starred + id).distinct() else starred - id)
    }
}
@Composable
internal fun WorkspaceExamples(values: WorkspaceValues, onChange: (WorkspaceValues) -> Unit, onPreview: () -> Unit) {
    val t = FoundryTheme.tokens
    Card {
        SectionHeader("Adaptive workspaces", subtitle = "Browse projects, trace a path and keep your selection as the layout changes.")
        BreadcrumbTrail(values.breadcrumbs, "${values.breadcrumbs.last().label}, current location", { onChange(values.breadcrumb(it)) })
        NavLink("Open adaptive workspace", onPreview, subtitle = "One pane on phones, two when there is room")
    }
    Card(role = SurfaceRole.FLOATING) {
        SectionHeader("Destination rail", subtitle = "The Shared destination is unavailable in this local example.")
        DestinationRail("Projects", values.destinations, values.destination.id, { onChange(values.navigate(it)) }, Modifier.width(130.dp).height(380.dp)) {
            WorkspaceIcon(it.id)
        }
        Text("Current collection: ${values.destination.label}", style = t.typography.caption)
    }
}
@Composable
private fun WorkspaceIcon(id: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(1.7f * unit)
        when (id) {
            "starred" -> {
                val path = Path()
                for (index in 0..9) {
                    val angle = -Math.PI / 2 + index * Math.PI / 5
                    val radius = (if (index % 2 == 0) 10f else 4.5f) * unit
                    val x = size.width / 2 + kotlin.math.cos(angle).toFloat() * radius
                    val y = size.height / 2 + kotlin.math.sin(angle).toFloat() * radius
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); drawPath(path, color, style = stroke)
            }
            "archived" -> {
                drawRect(color, Offset(3 * unit, 5 * unit), Size(18 * unit, 4 * unit), style = stroke)
                drawRect(color, Offset(5 * unit, 9 * unit), Size(14 * unit, 12 * unit), style = stroke)
                drawLine(color, Offset(9 * unit, 13 * unit), Offset(15 * unit, 13 * unit), stroke.width)
            }
            "shared" -> {
                drawCircle(color, 3 * unit, Offset(8 * unit, 7 * unit), style = stroke)
                drawCircle(color, 3 * unit, Offset(17 * unit, 8 * unit), style = stroke)
                drawArc(color, 180f, 180f, false, Offset(2 * unit, 13 * unit), Size(12 * unit, 12 * unit), style = stroke)
                drawArc(color, 180f, 180f, false, Offset(12 * unit, 14 * unit), Size(10 * unit, 10 * unit), style = stroke)
            }
            else -> for (row in 0..1) for (column in 0..1) {
                drawRect(color, Offset((3 + column * 11) * unit, (3 + row * 11) * unit), Size(7 * unit, 7 * unit), style = stroke)
            }
        }
    }
}
@Composable
internal fun WorkspacePreviewScreen(values: WorkspaceValues, onChange: (WorkspaceValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Adaptive workspace", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        WorkspaceContent(values, onChange, Modifier.weight(1f))
    }
}
@Composable
internal fun WorkspaceContent(values: WorkspaceValues, onChange: (WorkspaceValues) -> Unit, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    Backdrop(modifier.fillMaxSize(), background = {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.surfaceGround.color, t.colors.accentTint.color, t.colors.surfaceGround.color))))
    }) {
        Column(Modifier.fillMaxSize().padding(t.space.page), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            ToggleField("Single pane preview", values.forceSingle, { onChange(values.copy(forceSingle = it)) })
            BreadcrumbTrail(values.breadcrumbs, "${values.breadcrumbs.last().label}, current location", { onChange(values.breadcrumb(it)) })
            SplitPane(values.detailPresented, Modifier.weight(1f).fillMaxWidth(), values.forceSingle, 350.dp, 320.dp,
                primary = { mode ->
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
                        if (mode == PaneMode.SPLIT) DestinationRail("Projects", values.destinations, values.destination.id,
                            { onChange(values.navigate(it)) }, Modifier.width(110.dp).fillMaxHeight()) { WorkspaceIcon(it.id) }
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("workspace-projects").padding(2.dp),
                            verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                            if (mode == PaneMode.SINGLE) Tabs("Project collections", WorkspaceDestination.entries, values.destination,
                                { onChange(values.navigate(it.id)) }, label = { it.label })
                            SectionHeader(values.destination.label, subtitle = "Choose a project to inspect.")
                            if (values.projects.isEmpty()) EmptyState("No projects here", "Star a project from All projects to include it here.")
                            values.projects.forEach { project ->
                                SelectionCard(values.selected == project.id, { onChange(values.open(project.id)) }) {
                                    Text(project.title, style = t.typography.label)
                                    Text(if (project.archived) "Archived project" else "Personal project", style = t.typography.caption)
                                }
                            }
                        }
                    }
                }, detail = { mode ->
                    if (mode == PaneMode.SINGLE && values.detailPresented) BackHandler { onChange(values.copy(detailPresented = false)) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("workspace-detail").padding(2.dp),
                        verticalArrangement = Arrangement.spacedBy(t.space.section)) {
                        if (mode == PaneMode.SINGLE) ActionButton({ onChange(values.copy(detailPresented = false)) }, variant = ButtonVariant.SECONDARY) { Text("Back to projects") }
                        val project = values.project
                        if (project != null) Card {
                            SectionHeader(project.title, subtitle = if (project.archived) "Archived project" else "Personal project")
                            Text(project.detail)
                            ToggleField("Star this project", project.id in values.starred, { onChange(values.setStarred(it, project.id)) })
                            KeyValueRow("Storage", "This device")
                            Text("Local preview. Your selection and stars stay above the pane layout.", style = t.typography.caption)
                        } else Card { EmptyState("Choose a project", "Select a project from the current collection.") }
                    }
                })
        }
    }
}
