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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.highlightedtext.HighlightSegment
import dev.mobilefoundry.ui.components.display.highlightedtext.HighlightedText
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.removablechip.RemovableChip
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.patterns.searchresultrow.SearchResultRow
import dev.mobilefoundry.ui.components.patterns.searchsuggestionrow.SearchSuggestionRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class DiscoveryTopic(val label: String) { ALL("All topics"), GRAPHICS("Graphics"), WRITING("Writing"), DESIGN("Design") }
internal data class DiscoveryFilters(val topic: DiscoveryTopic = DiscoveryTopic.ALL, val archived: Boolean = false)
internal data class DiscoveryRecord(val id: String, val title: String, val excerpt: String, val topic: DiscoveryTopic, val archived: Boolean) {
    companion object { val all = listOf(
        DiscoveryRecord("motion", "Motion study", "Explore motion, light and material in a small scene.", DiscoveryTopic.GRAPHICS, false),
        DiscoveryRecord("metal", "Metal notes", "Notes on shaders, textures and GPU frame ownership.", DiscoveryTopic.GRAPHICS, false),
        DiscoveryRecord("field", "Offline field notes", "Collect notes offline and review them when connected.", DiscoveryTopic.WRITING, false),
        DiscoveryRecord("sketch", "Sketch archive", "Archived sketches of interfaces and motion experiments.", DiscoveryTopic.DESIGN, true),
        DiscoveryRecord("interface", "Interface patterns", "Reusable forms, navigation and accessible interactions.", DiscoveryTopic.DESIGN, false),
        DiscoveryRecord("sync", "Sync checklist", "A checklist for syncing notes without losing edits.", DiscoveryTopic.WRITING, false)) }
}
internal data class DiscoveryValues(val query: String = "", val filters: DiscoveryFilters = DiscoveryFilters(), val enabled: Boolean = true,
    val saved: List<String> = emptyList(), val recent: List<String> = emptyList(), val opened: String? = null, val opens: Int = 0) {
    val term get() = query.trim()
    val results get() = DiscoveryRecord.all.filter { row ->
        (filters.archived || !row.archived) && (filters.topic == DiscoveryTopic.ALL || filters.topic == row.topic) &&
        (term.isEmpty() || "${row.title} ${row.excerpt} ${row.topic.label}".contains(term, ignoreCase = true))
    }
    val openedTitle get() = DiscoveryRecord.all.firstOrNull { it.id == opened }?.title ?: "No record yet"
    fun setQuery(value: String) = if (enabled) copy(query = value) else this
    fun useSuggestion(value: String) = if (enabled && value.isNotBlank()) copy(query = value).rememberQuery() else this
    fun rememberQuery() = if (enabled && term.isNotEmpty()) copy(recent = listOf(term) + recent.filterNot { it.equals(term, ignoreCase = true) }.take(2)) else this
    fun clearRecent() = if (enabled) copy(recent = emptyList()) else this
    fun apply(draft: DiscoveryFilters) = if (enabled) copy(filters = draft) else this
    fun open(id: String) = if (enabled && results.any { it.id == id }) copy(opened = id, opens = opens + 1).rememberQuery() else this
    fun toggleSaved(id: String) = if (enabled && results.any { it.id == id }) copy(saved = if (id in saved) saved - id else saved + id) else this
}
/** The fixture styles its first literal match; the reusable text receives complete runs rather than offsets. */
internal fun discoverySegments(text: String, query: String): List<HighlightSegment> {
    val term = query.trim()
    val index = if (term.isEmpty()) -1 else text.indexOf(term, ignoreCase = true)
    return if (index < 0) listOf(HighlightSegment(text)) else listOf(HighlightSegment(text.substring(0, index)),
        HighlightSegment(text.substring(index, index + term.length), true), HighlightSegment(text.substring(index + term.length)))
}
@Composable
internal fun DiscoveryExamples(values: DiscoveryValues, onChange: (DiscoveryValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Search and discovery", subtitle = "Useful suggestions, readable results and filters you apply explicitly.")
        NavLink("Open search workspace", onPreview, subtitle = "Search local records, save a result and compare filter drafts")
    }
    DiscoveryContent(values, onChange)
}
@Composable
internal fun DiscoveryPreviewScreen(values: DiscoveryValues, onChange: (DiscoveryValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Search workspace", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("discovery-scroll").padding(20.dp)) {
            DiscoveryContent(values, onChange)
        }
    }
}
@Composable
internal fun DiscoveryContent(values: DiscoveryValues, onChange: (DiscoveryValues) -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    var showFilters by remember(values.enabled) { mutableStateOf(false) }
    var draft by remember { mutableStateOf(DiscoveryFilters()) }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable discovery actions", values.enabled, { onChange(values.copy(enabled = it)) })
            SearchField("Search library", values.query, { onChange(values.setQuery(it)) }, "Clear search", Modifier.fillMaxWidth(),
                onSubmit = { focus.clearFocus(); onChange(values.rememberQuery()) }, enabled = values.enabled)
            ActionButton({ focus.clearFocus(); draft = values.filters; showFilters = true }, variant = ButtonVariant.SECONDARY,
                enabled = values.enabled) { Text("Filters") }
            Text("Applied topic: ${values.filters.topic.label}", style = t.typography.caption)
            Text(if (values.filters.archived) "Including archived records" else "Active records only", style = t.typography.caption)
            WrapLayout {
                if (values.filters.topic != DiscoveryTopic.ALL) RemovableChip(values.filters.topic.label, "Remove topic filter",
                    { onChange(values.apply(values.filters.copy(topic = DiscoveryTopic.ALL))) }, enabled = values.enabled)
                if (values.filters.archived) RemovableChip("Archived", "Remove archived filter",
                    { onChange(values.apply(values.filters.copy(archived = false))) }, enabled = values.enabled)
            }
        }
        if (values.term.isEmpty()) Card {
            SectionHeader("Suggested searches")
            listOf("motion", "notes", "metal").forEach { term ->
                SearchSuggestionRow(term, "Search for $term", { onChange(values.useSuggestion(term)) }, detail = "Explore the library", enabled = values.enabled)
            }
            if (values.recent.isNotEmpty()) {
                SectionHeader("Recent searches")
                values.recent.forEach { term -> key(term) {
                    SearchSuggestionRow(term, "Search again for $term", { onChange(values.useSuggestion(term)) }, detail = "Recent search", enabled = values.enabled)
                } }
                ActionButton({ onChange(values.clearRecent()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Clear recent searches") }
            }
        }
        SectionHeader("Results", subtitle = "${values.results.size} records")
        if (values.results.isEmpty()) Card { EmptyState("No matching records", "Try another search or change the applied filters.") }
        values.results.forEach { row -> key(row.id) {
            Card {
                SearchResultRow(row.title, "Open ${row.title}. ${row.topic.label}. ${row.excerpt}",
                    { focus.clearFocus(); onChange(values.open(row.id)) }, detail = row.topic.label + if (row.archived) " · Archived" else "",
                    enabled = values.enabled, preview = { HighlightedText(discoverySegments(row.excerpt, values.term)) }, actions = {
                        ActionButton({ onChange(values.toggleSaved(row.id)) }, Modifier.semantics {
                            contentDescription = "${if (row.id in values.saved) "Unsave" else "Save"} ${row.title}"
                            stateDescription = if (row.id in values.saved) "Saved" else "Not saved"
                        }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text(if (row.id in values.saved) "Saved" else "Save") }
                    })
            }
        } }
        Card(role = SurfaceRole.FLOATING) {
            KeyValueRow("Last opened", values.openedTitle)
            Text("Records opened: ${values.opens}", style = t.typography.caption)
            Text("Saved records: ${values.saved.size}", style = t.typography.caption)
            Text("Local library preview. Searches and bookmarks stay in this example.", style = t.typography.caption)
        }
    }
    SheetPanel("Filter records", showFilters, { showFilters = false }, "Discard filters") {
        Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            SelectField("Topic", DiscoveryTopic.entries, draft.topic, { draft = draft.copy(topic = it) }, enabled = values.enabled, label = { it.label })
            ToggleField("Include archived", draft.archived, { draft = draft.copy(archived = it) }, enabled = values.enabled)
            Text("Only Apply filters changes the results.", style = t.typography.caption)
            ActionButton({ draft = DiscoveryFilters() }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Reset filter draft") }
            ActionButton({ onChange(values.apply(draft)); showFilters = false }, enabled = values.enabled) { Text("Apply filters") }
        }
    }
}
