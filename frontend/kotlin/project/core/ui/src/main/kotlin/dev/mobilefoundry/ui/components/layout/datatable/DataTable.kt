package dev.mobilefoundry.ui.components.layout.datatable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.theme.FoundryTheme

data class DataTableColumn(val id: String, val label: String, val width: Dp = 160.dp) {
    init { require(width.value.isFinite() && width.value > 0) }
}

/** Small eager table. Host owns vertical scroll, data/sort/pages and contextual cell narration.
 * Column widths include padding. Slots retain independent native control semantics. */
@Composable
fun <R> DataTable(title: String, rows: List<R>, columns: List<DataTableColumn>, rowKey: (R) -> String,
    modifier: Modifier = Modifier, scrollState: ScrollState = rememberScrollState(),
    header: @Composable (DataTableColumn) -> Unit, cell: @Composable (R, DataTableColumn) -> Unit) {
    require(columns.isNotEmpty() && columns.map { it.id }.distinct().size == columns.size)
    val ids = rows.map(rowKey)
    require(ids.distinct().size == rows.size)
    val width = columns.sumOf { it.width.value.toDouble() }.toFloat().dp
    require(width.value.isFinite())
    val t = FoundryTheme.tokens
    Surface(modifier.semantics { contentDescription = title; isTraversalGroup = true }) {
        Column(Modifier.horizontalScroll(scrollState).width(width)) {
            Row(Modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive).background(t.colors.surfaceSunk.color),
                verticalAlignment = Alignment.CenterVertically) {
                columns.forEach { column -> key(column.id) {
                    Box(Modifier.width(column.width).padding(t.space.inline)) { header(column) }
                } }
            }
            rows.forEachIndexed { index, row -> key(ids[index]) {
                HorizontalDivider(color = t.colors.line.color)
                Row(Modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive).semantics { isTraversalGroup = true },
                    verticalAlignment = Alignment.CenterVertically) {
                    columns.forEach { column -> key(column.id) {
                        Box(Modifier.width(column.width).padding(t.space.inline)) { cell(row, column) }
                    } }
                }
            } }
        }
    }
}
