package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.filetypemark.FileTypeMark
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.patterns.treerow.TreeDisclosure
import dev.mobilefoundry.ui.components.patterns.treerow.TreeRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale

internal data class BrowserItem(val id: String, val parent: String?, val title: String, val format: String, val detail: String,
    val folder: Boolean = false, val available: Boolean = true) {
    val path: String get() = parent?.let(::find)?.let { "${it.path} / $title" } ?: title
    companion object {
        val fixtures = listOf(
            BrowserItem("atlas", null, "Atlas", "DIR", "2 items", folder = true),
            BrowserItem("studies", "atlas", "Studies", "DIR", "2 items", folder = true),
            BrowserItem("refs", "studies", "References", "DIR", "1 item", folder = true),
            BrowserItem("field", "refs", "Field image.jpg", "JPEG", "JPEG · 860 KB"),
            BrowserItem("lens", "studies", "Lens study.png", "PNG", "PNG · 1.2 MB"),
            BrowserItem("brief", "atlas", "Brief.md", "MD", "Markdown · 4 KB"),
            BrowserItem("notes", null, "Field notes.md", "MD", "Markdown · 2 KB"),
            BrowserItem("locked", null, "Restricted.txt", "TXT", "Unavailable in this preview", available = false))
        fun find(id: String) = fixtures.firstOrNull { it.id == id }
    }
}
internal data class BrowserEntry(val item: BrowserItem, val depth: Int)
/** Projection over the bounded, acyclic fixture tree. Visibility never rewrites saved expansion or selected identity. */
internal data class FileBrowserValues(val query: String = "", val expanded: List<String> = listOf("atlas", "studies"),
    val favorites: List<String> = emptyList(), val selected: String? = null, val opens: Int = 0,
    val enabled: Boolean = true, val empty: Boolean = false) {
    val searching get() = query.trim().isNotEmpty()
    val canEdit get() = enabled && !empty
    private val searchIDs: Set<String> get() {
        val term = query.trim().lowercase(Locale.ROOT)
        val included = mutableSetOf<String>()
        BrowserItem.fixtures.filter { it.title.lowercase(Locale.ROOT).contains(term) }.forEach { item ->
            var next: BrowserItem? = item
            while (next != null) { val current = next; included += current.id; next = current.parent?.let(BrowserItem::find) }
        }
        return included
    }
    val entries: List<BrowserEntry> get() {
        if (empty) return emptyList()
        val included = if (searching) searchIDs else null
        val result = mutableListOf<BrowserEntry>()
        fun visit(parent: String?, depth: Int) {
            BrowserItem.fixtures.filter { it.parent == parent }.forEach { item ->
                if (included == null || item.id in included) {
                    result += BrowserEntry(item, depth)
                    if (item.folder && (searching || item.id in expanded)) visit(item.id, depth + 1)
                }
            }
        }
        visit(null, 0)
        return result
    }
    fun isExpanded(id: String) = if (searching) BrowserItem.fixtures.any { it.parent == id && it.id in searchIDs } else id in expanded
    fun canOpen(id: String) = canEdit && BrowserItem.find(id)?.available == true && entries.any { it.item.id == id }
    val canInspect get() = canEdit && selected?.let(BrowserItem::find)?.available == true
    fun search(text: String) = if (enabled) copy(query = text) else this
    fun toggle(id: String): FileBrowserValues {
        if (!canEdit || searching || BrowserItem.find(id)?.folder != true || entries.none { it.item.id == id }) return this
        return copy(expanded = if (id in expanded) expanded - id else expanded + id)
    }
    fun open(id: String) = if (canOpen(id)) copy(selected = id, opens = opens + 1) else this
    fun favorite(id: String) = if (canEdit && BrowserItem.find(id)?.available == true) copy(favorites = if (id in favorites) favorites - id else favorites + id) else this
    fun collapseAll() = if (canEdit && !searching) copy(expanded = emptyList()) else this
    fun resetBrowser() = if (enabled) copy(query = "", expanded = listOf("atlas", "studies"), selected = null, empty = false) else this
}
@Composable
internal fun FileExamples(values: FileBrowserValues, onChange: (FileBrowserValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Files and hierarchy", subtitle = "Open rows, disclose branches and keep related actions independent.")
        NavLink("Open files preview", onPreview, subtitle = "Explore folders, search, favorites and an inspector")
    }
    FileContent(values, onChange)
}
@Composable
internal fun FilePreviewScreen(values: FileBrowserValues, onChange: (FileBrowserValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Files preview", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("files-scroll").padding(20.dp)) { FileContent(values, onChange) }
    }
}
@Composable
internal fun FileContent(values: FileBrowserValues, onChange: (FileBrowserValues) -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    var showInspector by remember { mutableStateOf(false) }
    LaunchedEffect(values.canInspect) { if (!values.canInspect) showInspector = false }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable file actions", values.enabled, { onChange(values.copy(enabled = it)) })
            ToggleField("Show empty browser", values.empty, { onChange(values.copy(empty = it)) }, enabled = values.enabled)
            SearchField("Search files", values.query, { onChange(values.search(it)) }, "Clear file search", onSubmit = { focus.clearFocus() }, enabled = values.enabled)
            Text("Local fixture files. Search reveals matching paths; clearing it restores your folder choices.", style = t.typography.caption)
            ActionButton({ onChange(values.collapseAll()) }, variant = ButtonVariant.SECONDARY, enabled = values.canEdit && !values.searching) { Text("Collapse all folders") }
            ActionButton({ onChange(values.resetBrowser()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Reset browser") }
            Text("File opens: ${values.opens} · Favorites: ${values.favorites.size}", style = t.typography.caption)
            Text("Last opened: ${values.selected?.let(BrowserItem::find)?.title ?: "None"}", style = t.typography.caption)
        }
        Card {
            SectionHeader("Workspace files", subtitle = "${values.entries.size} visible ${if (values.entries.size == 1) "item" else "items"}")
            if (values.entries.isEmpty()) EmptyState(if (values.empty) "No files yet" else "No matching files",
                message = if (values.empty) "Turn off the empty state to restore this preview." else "Try another name or clear your search.")
            values.entries.forEach { entry -> key(entry.item.id) {
                val item = entry.item
                val expanded = values.isExpanded(item.id)
                TreeRow(item.title, "${if (item.available) "Open" else "Unavailable"} ${item.title}, ${if (item.folder) "Folder, " else ""}${item.detail}, Level ${entry.depth + 1}",
                    onOpen = { if (values.canOpen(item.id)) { val next = values.open(item.id); onChange(next); focus.clearFocus(); showInspector = next.canInspect } },
                    subtitle = "${item.detail} · Level ${entry.depth + 1}", depth = entry.depth, selected = values.selected == item.id, enabled = values.canOpen(item.id),
                    disclosure = if (item.folder) TreeDisclosure(expanded, "${if (expanded) "Collapse" else "Expand"} ${item.title}",
                        if (values.searching) (if (expanded) "Expanded for search" else "No matching children") else if (expanded) "Expanded" else "Collapsed",
                        enabled = values.canEdit && !values.searching, onToggle = { onChange(values.toggle(item.id)) }) else null,
                    leading = { FileTypeMark(item.format, if (item.folder) "Folder" else item.format) }, actions = {
                        IconAction("${if (item.id in values.favorites) "Unfavorite" else "Favorite"} ${item.title}", { onChange(values.favorite(item.id)) },
                            Modifier.semantics { selected = item.id in values.favorites }, enabled = values.canEdit && item.available) {
                            Text(if (item.id in values.favorites) "★" else "☆")
                        }
                    })
            } }
        }
    }
    SheetPanel("File inspector", showInspector, { showInspector = false }, "Close file inspector", modifier = Modifier.testTag("file-inspector")) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            values.selected?.let(BrowserItem::find)?.let { item ->
                FileTypeMark(item.format, if (item.folder) "Folder" else item.format)
                SectionHeader(item.title, subtitle = "Preview metadata")
                KeyValueRow("Path", item.path)
                KeyValueRow("Type", if (item.folder) "Folder" else item.format)
                KeyValueRow("Details", item.detail)
                ActionButton({ onChange(values.favorite(item.id)) }, enabled = values.canInspect) {
                    Text(if (item.id in values.favorites) "Remove inspector favorite" else "Favorite from inspector")
                }
            }
        }
    }
}
