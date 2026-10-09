package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.toast.ToastAction
import dev.mobilefoundry.ui.components.feedback.toast.ToastBanner
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.checkbox.CheckState
import dev.mobilefoundry.ui.components.forms.checkbox.Checkbox
import dev.mobilefoundry.ui.components.forms.removablechip.RemovableChip
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.forms.tokenfield.TokenField
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.menu.ActionMenu
import dev.mobilefoundry.ui.components.overlays.menu.MenuAction
import dev.mobilefoundry.ui.components.patterns.actionbar.ActionBar
import dev.mobilefoundry.ui.components.patterns.selectionrow.SelectionRow
import dev.mobilefoundry.ui.components.patterns.swipeactionrow.SwipeAction
import dev.mobilefoundry.ui.components.patterns.swipeactionrow.SwipeActionRow
import dev.mobilefoundry.ui.theme.FoundryTheme

internal data class LibraryPreviewItem(val id: String, val title: String) {
    companion object {
        val all = listOf(LibraryPreviewItem("orbit", "Orbit study"), LibraryPreviewItem("field", "Field notes"),
            LibraryPreviewItem("atlas", "Atlas workspace"), LibraryPreviewItem("material", "Material references"),
            LibraryPreviewItem("motion", "Motion sketches"), LibraryPreviewItem("light", "Light experiments"))
    }
}
internal data class EditingValues(val tagDraft: String = "", val tags: List<String> = listOf("Sketch", "Review"), val search: String = "",
    val selected: List<String> = emptyList(), val archived: List<String> = emptyList(), val removed: List<String> = emptyList(),
    val undoIDs: List<String> = emptyList(), val undoSelection: List<String> = emptyList(), val enabled: Boolean = true) {
    val visible get() = LibraryPreviewItem.all.filter { it.id !in removed && (search.isEmpty() || it.title.contains(search, ignoreCase = true)) }
    val hiddenSelected get() = (selected.toSet() - visible.map { it.id }.toSet()).size
    private val duplicate get() = tags.any { it.equals(tagDraft.trim(), ignoreCase = true) }
    val canAdd get() = enabled && tags.size < 6 && tagDraft.isNotBlank() && !duplicate
    val tagError get() = if (tags.size >= 6) "Six tags already added." else if (duplicate) "This tag is already present." else null
    fun addTag() = if (canAdd) copy(tags = tags + tagDraft.trim(), tagDraft = "") else this
    fun removeTag(tag: String) = if (enabled) copy(tags = tags - tag) else this
    fun toggle(id: String): EditingValues {
        if (!enabled || id in removed || LibraryPreviewItem.all.none { it.id == id }) return this
        return copy(selected = if (id in selected) selected - id else selected + id)
    }
    fun selectVisible(): EditingValues {
        if (!enabled || visible.isEmpty()) return this
        val ids = visible.map { it.id }
        return copy(selected = if (selected.containsAll(ids)) selected - ids.toSet() else (selected + ids).distinct())
    }
    fun archive(ids: List<String>) = if (enabled) copy(archived = (archived + admit(ids)).distinct()) else this
    fun toggleArchive(id: String): EditingValues {
        if (!enabled || admit(listOf(id)).isEmpty()) return this
        return copy(archived = if (id in archived) archived - id else archived + id)
    }
    fun remove(ids: List<String>): EditingValues {
        val admitted = admit(ids)
        if (!enabled || admitted.isEmpty()) return this
        return copy(undoIDs = admitted, undoSelection = selected.filter { it in admitted },
            removed = (removed + admitted).distinct(), selected = selected - admitted.toSet())
    }
    fun undo() = if (enabled && undoIDs.isNotEmpty()) copy(removed = removed - undoIDs.toSet(),
        selected = (selected + undoSelection).distinct(), undoIDs = emptyList(), undoSelection = emptyList()) else this
    private fun admit(ids: List<String>) = ids.distinct().filter { id -> id !in removed && LibraryPreviewItem.all.any { it.id == id } }
}
@Composable
internal fun EditingExamples(values: EditingValues, onChange: (EditingValues) -> Unit, onPreview: () -> Unit) {
    ToggleField("Enable editing controls", values.enabled, { onChange(values.copy(enabled = it)) })
    Card { LibraryTags(values, onChange) }
    Card {
        SelectionRow("Orbit study", "orbit" in values.selected, if ("orbit" in values.selected) "Selected" else "Not selected",
            { onChange(values.toggle("orbit")) }, subtitle = if ("orbit" in values.archived) "Archived" else "Local study",
            enabled = values.enabled && "orbit" !in values.removed)
        Text("Selected items: ${values.selected.size}")
        NavLink("Open library editor", onPreview, subtitle = "Selection, filtering, swipe actions and undo")
    }
}
@Composable
private fun LibraryTags(values: EditingValues, onChange: (EditingValues) -> Unit) {
    TokenField("New library tag", values.tagDraft, { onChange(values.copy(tagDraft = it)) }, "Add library tag", values.canAdd,
        { onChange(values.addTag()) }, enabled = values.enabled, help = "One tag at a time. Up to six.", error = values.tagError) { interactive ->
        values.tags.forEach { tag -> key(tag) {
            RemovableChip(tag, "Remove tag $tag", { onChange(values.removeTag(tag)) }, enabled = interactive)
        } }
    }
}
@Composable
internal fun LibraryEditingScreen(values: EditingValues, onChange: (EditingValues) -> Unit, onBack: () -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    val back = { focus.clearFocus(); onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().imePadding()) {
        TextButton(back) { Text("Back to components") }
        Text("Library editor", style = t.typography.heading, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("editing-list"), contentPadding = PaddingValues(t.space.page),
            verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
            item(key = "tags") { Card { LibraryTags(values, onChange) } }
            item(key = "filter") { SearchField("Filter library", values.search, { onChange(values.copy(search = it)) }, "Clear library filter") }
            item(key = "selection") {
                val ids = values.visible.map { it.id }
                val state = if (ids.isEmpty() || ids.none { it in values.selected }) CheckState.OFF
                    else if (values.selected.containsAll(ids)) CheckState.ON else CheckState.MIXED
                Checkbox("Select visible items", state, when (state) {
                    CheckState.ON -> "All visible selected"; CheckState.MIXED -> "Some visible selected"; CheckState.OFF -> "None visible selected"
                }, { onChange(values.selectVisible()) }, enabled = values.enabled && ids.isNotEmpty())
                Text("Visible items: ${values.visible.size} · Archived: ${(values.archived.toSet() - values.removed.toSet()).size}")
                Text("Swipe toward the end to archive, or toward the start to remove. Row menus offer the same actions. Local preview only.", style = t.typography.caption)
            }
            if (values.visible.isEmpty()) item(key = "empty") { Text("No matching library items. Clear the filter or undo removal.") }
            items(values.visible, key = { it.id }) { item ->
                val archiveLabel = "${if (item.id in values.archived) "Unarchive" else "Archive"} ${item.title}"
                val archive = { onChange(values.toggleArchive(item.id)) }
                val remove = { onChange(values.remove(listOf(item.id))) }
                SwipeActionRow("Actions for library item ${item.title}", Modifier.testTag("editing-row-${item.id}"),
                    leading = SwipeAction(archiveLabel, archive), trailing = SwipeAction("Remove ${item.title}", remove, destructive = true), enabled = values.enabled) {
                    Card {
                        SelectionRow(item.title, item.id in values.selected, if (item.id in values.selected) "Selected" else "Not selected",
                            { onChange(values.toggle(item.id)) }, enabled = values.enabled,
                            subtitle = if (item.id in values.archived) "Archived" else "Local study")
                        ActionMenu("Actions for ${item.title}", listOf(MenuAction("archive", archiveLabel, enabled = values.enabled, onSelect = archive),
                            MenuAction("remove", "Remove ${item.title}", destructive = true, enabled = values.enabled, onSelect = remove)))
                    }
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            if (values.undoIDs.isNotEmpty()) ToastBanner("Removed ${values.undoIDs.size} preview items", "Dismiss removal notice",
                { onChange(values.copy(undoIDs = emptyList(), undoSelection = emptyList())) },
                action = if (values.enabled) ToastAction("Undo removal", { onChange(values.undo()) }) else null)
            ActionBar(summary = "${values.selected.size} selected · ${values.hiddenSelected} hidden") {
                WrapLayout {
                    ActionButton({ onChange(values.archive(values.selected)) }, variant = ButtonVariant.SECONDARY,
                        enabled = values.enabled && values.selected.isNotEmpty()) { Text("Archive selected") }
                    ActionButton({ onChange(values.remove(values.selected)) }, variant = ButtonVariant.DESTRUCTIVE,
                        enabled = values.enabled && values.selected.isNotEmpty()) { Text("Remove selected") }
                    ActionButton({ onChange(values.copy(selected = emptyList())) }, variant = ButtonVariant.QUIET,
                        enabled = values.enabled && values.selected.isNotEmpty()) { Text("Clear selection") }
                }
            }
        }
    }
}
