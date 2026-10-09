package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.divider.DividerAxis
import dev.mobilefoundry.ui.components.layout.divider.SectionDivider
import dev.mobilefoundry.ui.components.layout.stack.HorizontalStack
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.navigation.tabbar.TabBar
import dev.mobilefoundry.ui.components.navigation.tabbar.TabItem
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.shells.appshell.AppShell
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class ShellDestination(val label: String, val icon: Int) {
    OVERVIEW("Overview", R.drawable.ic_tab_home), ACTIVITY("Activity", R.drawable.ic_tab_library), SETTINGS("Settings", R.drawable.ic_tab_account)
}
enum class ShellSpacing(val label: String) { THEME("Theme defaults"), COMPACT("Compact"), RELAXED("Relaxed");
    val value get() = when (this) { THEME -> null; COMPACT -> 4.dp; RELAXED -> 24.dp }
}
/** App-owned identity and page values; native tab/content composition does not own their lifetime. */
data class ShellValues(val selected: ShellDestination = ShellDestination.OVERVIEW, val spacing: ShellSpacing = ShellSpacing.THEME,
    val showNavigation: Boolean = true, val markers: Map<String, Int> = emptyMap(), val selections: Int = 0) {
    fun select(id: String): ShellValues {
        val destination = ShellDestination.entries.firstOrNull { it.name == id } ?: return this
        return if (destination == selected) this else copy(selected = destination, selections = selections + 1)
    }
    fun addMarker() = copy(markers = markers + (selected.name to ((markers[selected.name] ?: 0) + 1)))
}
@Composable
fun ShellExamples(values: ShellValues, onChange: (ShellValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Stacks, separators and shells", "Native layouts with caller-owned navigation and feature state.")
        NavLink("Open shell preview", onPreview, subtitle = "Try tabs, spacing and retained page values")
    }
    ShellContent(values, onChange)
}
@Composable
fun ShellPreviewScreen(values: ShellValues, onChange: (ShellValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val t = FoundryTheme.tokens
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back to components") }
        Text("Shell preview", style = t.typography.title)
        AppShell(Modifier.weight(1f).fillMaxWidth(), background = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.surfaceGround.color, t.colors.accentTint.color))))
        }, content = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { ShellContent(values, onChange) }
        }, navigation = {
            if (values.showNavigation) TabBar(ShellDestination.entries.map { destination ->
                TabItem(destination.name, destination.label) { Icon(painterResource(destination.icon), contentDescription = null) }
            }, values.selected.name, { onChange(values.select(it)) }, Modifier.padding(horizontal = t.space.page, vertical = t.space.stack))
        })
    }
}
@Composable
fun ShellContent(values: ShellValues, onChange: (ShellValues) -> Unit) {
    val t = FoundryTheme.tokens
    VerticalStack(spacing = t.space.section) {
        Card(role = SurfaceRole.FLOATING) {
            SelectField("Stack spacing", ShellSpacing.entries, values.spacing, { onChange(values.copy(spacing = it)) }, label = { it.label })
            ToggleField("Show bottom navigation", values.showNavigation, { onChange(values.copy(showNavigation = it)) })
            SelectField("Preview destination", ShellDestination.entries, values.selected, { onChange(values.select(it.name)) }, label = { it.label })
            Text("Tab changes: ${values.selections}", style = t.typography.caption)
        }
        Card {
            VerticalStack(spacing = values.spacing.value) {
                SectionHeader("${values.selected.label} workspace", "Each destination keeps its own marker count.")
                SectionDivider(inset = t.space.inline)
                Text("${values.selected.label} markers: ${values.markers[values.selected.name] ?: 0}", style = t.typography.body)
                ActionButton({ onChange(values.addMarker()) }, variant = ButtonVariant.SECONDARY) { Text("Add marker") }
                HorizontalStack(Modifier.height(44.dp), spacing = values.spacing.value) {
                    Text("Draft", style = t.typography.caption)
                    SectionDivider(axis = DividerAxis.VERTICAL, inset = 8.dp)
                    Text("Local", style = t.typography.caption)
                }
                Text("Stacks use theme spacing by default. The shell host owns scrolling, routes and safe areas.", style = t.typography.caption, color = t.colors.inkSecondary.color)
            }
        }
    }
}
