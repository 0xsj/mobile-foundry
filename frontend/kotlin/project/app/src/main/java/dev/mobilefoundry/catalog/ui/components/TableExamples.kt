package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.datatable.DataTable
import dev.mobilefoundry.ui.components.layout.datatable.DataTableColumn
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.navigation.pagination.PaginationBar
import dev.mobilefoundry.ui.components.navigation.tablesortheader.TableSortOrder
import dev.mobilefoundry.ui.components.navigation.tablesortheader.TableSortHeader
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class TableSortKey(val id: String, val label: String) { PROJECT("project", "Project"), SESSIONS("sessions", "Sessions"), STATUS("status", "Status") }
internal data class LedgerRecord(val id: String, val title: String, val sessions: Int, val status: String) {
    companion object { val all = listOf(
        LedgerRecord("atlas", "Atlas workspace", 12, "Ready"), LedgerRecord("orbit", "Orbit study", 4, "Review"),
        LedgerRecord("field", "Field notes", 8, "Draft"), LedgerRecord("meadow", "Meadow journal", 16, "Ready"),
        LedgerRecord("harbor", "Harbor archive", 6, "Review"), LedgerRecord("lumen", "Lumen sketches", 10, "Draft"),
        LedgerRecord("delta", "Delta research", 2, "Review"), LedgerRecord("studio", "Studio collection", 14, "Ready"),
        LedgerRecord("vista", "Vista exploration", 18, "Draft")) }
}
internal data class TableValues(val sortKey: TableSortKey = TableSortKey.PROJECT, val order: TableSortOrder = TableSortOrder.ASCENDING,
    val page: Int = 1, val enabled: Boolean = true, val empty: Boolean = false, val inspected: String? = null, val inspections: Int = 0) {
    companion object { val columns = listOf(DataTableColumn("project", "Project", 220.dp), DataTableColumn("sessions", "Sessions", 180.dp),
        DataTableColumn("status", "Status", 150.dp), DataTableColumn("open", "Inspect", 130.dp)) }
    val sorted get() = LedgerRecord.all.sortedWith { left, right ->
        val comparison = when (sortKey) {
            TableSortKey.PROJECT -> left.title.compareTo(right.title)
            TableSortKey.SESSIONS -> left.sessions.compareTo(right.sessions)
            TableSortKey.STATUS -> left.status.compareTo(right.status)
        }
        if (comparison == 0) left.id.compareTo(right.id) else if (order == TableSortOrder.ASCENDING) comparison else -comparison
    }
    val rows get() = if (empty) emptyList() else sorted.drop((page - 1) * 3).take(3)
    val totalPages get() = if (empty) 1 else 3
    val shownPage get() = if (empty) 1 else page
    val orderLabel get() = if (order == TableSortOrder.ASCENDING) "Ascending" else "Descending"
    val inspectedTitle get() = LedgerRecord.all.firstOrNull { it.id == inspected }?.title ?: "No project yet"
    fun sort(key: TableSortKey) = if (!enabled) this else copy(sortKey = key,
        order = if (sortKey == key && order == TableSortOrder.ASCENDING) TableSortOrder.DESCENDING else TableSortOrder.ASCENDING)
    fun changePage(value: Int) = if (enabled && !empty && value in 1..totalPages) copy(page = value) else this
    fun inspect(id: String) = if (enabled && rows.any { it.id == id }) copy(inspected = id, inspections = inspections + 1) else this
}
@Composable
internal fun TableExamples(values: TableValues, onChange: (TableValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Tables and pages", subtitle = "Readable columns, explicit sorting and controlled pages.")
        NavLink("Open project ledger", onPreview, subtitle = "Scroll across records, sort columns and inspect a page")
    }
    TableContent(values, onChange)
}
@Composable
internal fun TablePreviewScreen(values: TableValues, onChange: (TableValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Project ledger", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("ledger-scroll").padding(20.dp)) {
            TableContent(values, onChange)
        }
    }
}
@Composable
internal fun TableContent(values: TableValues, onChange: (TableValues) -> Unit) {
    val t = FoundryTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable ledger actions", values.enabled, { onChange(values.copy(enabled = it)) })
            ToggleField("Show empty records", values.empty, { onChange(values.copy(empty = it)) })
            Text("Sorted by ${values.sortKey.label}, ${values.orderLabel.lowercase()}", style = t.typography.caption)
            Text("Scroll horizontally to see every column. Sorting keeps the current page.", style = t.typography.caption)
        }
        DataTable("Project records", values.rows, TableValues.columns, { it.id }, Modifier.fillMaxWidth().testTag("ledger-table"),
            header = { column ->
                val sort = TableSortKey.entries.firstOrNull { it.id == column.id }
                if (sort != null) TableSortHeader(column.label, if (values.sortKey == sort) values.order else null,
                    if (values.sortKey == sort) values.orderLabel else "Not sorted", { onChange(values.sort(sort)) },
                    Modifier.semantics { contentDescription = "Sort by ${column.label}" }, enabled = values.enabled)
                else Text(column.label, Modifier.semantics { heading() }, style = t.typography.label)
            }, cell = { row, column ->
                when (column.id) {
                    "project" -> Text(row.title, Modifier.semantics { contentDescription = "Project: ${row.title}" }, style = t.typography.label)
                    "sessions" -> Text("${row.sessions}", Modifier.semantics { contentDescription = "${row.title}, sessions: ${row.sessions}" })
                    "status" -> Box(Modifier.semantics(mergeDescendants = true) { contentDescription = "${row.title}, status: ${row.status}" }) { Badge(row.status) }
                    else -> ActionButton({ onChange(values.inspect(row.id)) }, Modifier.semantics { contentDescription = "Inspect ${row.title}" },
                        variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Inspect") }
                }
            })
        if (values.empty) Card { EmptyState("No project records", "Turn off empty records to restore the retained page.") }
        Card(role = SurfaceRole.FLOATING) {
            PaginationBar(values.shownPage, values.totalPages, "Page ${values.shownPage} of ${values.totalPages}", "Previous page", "Next page",
                { onChange(values.changePage(it)) }, enabled = values.enabled && !values.empty)
            KeyValueRow("Last inspected", values.inspectedTitle)
            Text("Inspections: ${values.inspections}", style = t.typography.caption)
            Text("Local fixture. Page controls and sort actions do not fetch records.", style = t.typography.caption)
        }
    }
}
