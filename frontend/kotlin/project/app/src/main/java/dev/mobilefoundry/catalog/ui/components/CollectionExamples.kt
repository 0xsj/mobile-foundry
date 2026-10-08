package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.checkbox.Checkbox
import dev.mobilefoundry.ui.components.forms.checkbox.CheckState
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.patterns.collectiontoolbar.CollectionToolbar
import dev.mobilefoundry.ui.theme.FoundryTheme

private data class CollectionExampleItem(val id: String, val title: String, val favorite: Boolean, val recency: Int)
private val collectionItems = listOf(CollectionExampleItem("atlas", "Atlas workspace", true, 2),
    CollectionExampleItem("orbit", "Orbit study", false, 3), CollectionExampleItem("field", "Field notes", true, 1))

@Composable
internal fun CollectionExamples(search: String, onSearch: (String) -> Unit, favorites: Boolean, onFavorites: (Boolean) -> Unit,
    sort: String, onSort: (String) -> Unit, selected: List<String>, onSelected: (List<String>) -> Unit) {
    val t = FoundryTheme.tokens
    val matches = collectionItems.filter { (!favorites || it.favorite) && (search.isEmpty() || it.title.contains(search, ignoreCase = true)) }
    val visible = if (sort == "Name") matches.sortedBy { it.title } else matches.sortedByDescending { it.recency }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            CollectionToolbar("Your collection", summary = "${visible.size} visible · ${selected.size} selected", filters = {
                SearchField("Filter collection", search, onSearch, "Clear collection search")
                ToggleField("Favorites only", favorites, onFavorites)
                SelectField("Sort projects", listOf("Name", "Newest"), sort, onSort, label = { it })
            }, actions = {
                ActionButton({ onSelected((selected + visible.map { it.id }).distinct()) },
                    variant = ButtonVariant.SECONDARY, enabled = visible.isNotEmpty()) { Text("Select visible") }
                ActionButton({ onSelected(emptyList()) }, variant = ButtonVariant.QUIET, enabled = selected.isNotEmpty()) { Text("Clear selection") }
            })
        }
        Card {
            if (visible.isEmpty()) EmptyState("Nothing matches these filters", "Change the search or include all projects.", actions = {
                ActionButton({ onSearch(""); onFavorites(false) }, variant = ButtonVariant.SECONDARY) { Text("Reset collection filters") }
            })
            else visible.forEach { item ->
                Checkbox(item.title, if (item.id in selected) CheckState.ON else CheckState.OFF,
                    if (item.id in selected) "Selected" else "Not selected", onToggle = {
                        onSelected(if (item.id in selected) selected - item.id else selected + item.id)
                    })
            }
        }
        Text("Filtering keeps selected IDs. Select visible adds the current matches; Clear selection removes every selection.", style = t.typography.caption)
    }
}
