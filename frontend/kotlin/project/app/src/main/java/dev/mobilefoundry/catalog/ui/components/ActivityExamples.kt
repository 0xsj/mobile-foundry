package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.avatar.Avatar
import dev.mobilefoundry.ui.components.display.avatargroup.AvatarGroup
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.expandabletext.ExpandableText
import dev.mobilefoundry.ui.components.display.timelineitem.TimelineItem
import dev.mobilefoundry.ui.components.feedback.loadmore.LoadMoreFooter
import dev.mobilefoundry.ui.components.feedback.loadmore.LoadMorePhase
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.patterns.refreshcontainer.RefreshContainer
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.delay

internal data class ActivityRecord(val id: Int) {
    val title get() = listOf("A new direction", "Material study", "Ready for review")[(id - 1) % 3] + " · $id"
    val timestamp get() = "Today · Update $id"
    val note get() = "We explored softer surfaces and a quieter palette for this collection. The latest study brings the image, motion, and supporting details together. There is room to adjust the rhythm as the collection grows, while keeping the important actions easy to find. Share your observations with the team before the next review."
}

/** Local, ephemeral fixture policy. The destination's LaunchedEffect owns delayed work. */
@Stable internal class ActivityPreviewValues {
    var records by mutableStateOf((1..3).map(::ActivityRecord)); private set
    var expanded by mutableStateOf(setOf<Int>()); private set
    var summaryExpanded by mutableStateOf(false)
    var failNextPage by mutableStateOf(false)
    var phase by mutableStateOf(LoadMorePhase.IDLE); private set
    var refreshing by mutableStateOf(false); private set
    var refreshes by mutableIntStateOf(0); private set
    var request by mutableIntStateOf(0); private set
    fun setExpanded(id: Int, value: Boolean) { expanded = if (value) expanded + id else expanded - id }
    fun requestPage() {
        if (refreshing || phase !in listOf(LoadMorePhase.IDLE, LoadMorePhase.FAILED)) return
        phase = LoadMorePhase.LOADING; request++
    }
    fun requestRefresh() {
        if (refreshing || phase == LoadMorePhase.LOADING) return
        refreshing = true; request++
    }
    suspend fun completeRequest() {
        if (!refreshing && phase != LoadMorePhase.LOADING) return
        try {
            delay(450)
            if (refreshing) {
                records = (1..3).map(::ActivityRecord); phase = LoadMorePhase.IDLE; refreshes++
                expanded = expanded.intersect(records.map { it.id }.toSet())
            } else if (failNextPage) { failNextPage = false; phase = LoadMorePhase.FAILED }
            else {
                records += (records.size + 1..records.size + 3).map(::ActivityRecord)
                phase = if (records.size >= 9) LoadMorePhase.EXHAUSTED else LoadMorePhase.IDLE
            }
        } finally { cancelPending() }
    }
    fun cancelPending() { refreshing = false; if (phase == LoadMorePhase.LOADING) phase = LoadMorePhase.IDLE }
    val message get() = when (phase) {
        LoadMorePhase.IDLE -> "More updates are available."
        LoadMorePhase.LOADING -> "Loading more updates…"
        LoadMorePhase.FAILED -> "Could not load more. Your updates are still here."
        LoadMorePhase.EXHAUSTED -> "You’re all caught up."
    }
}
private data class ActivityMember(val name: String, val initials: String)

@Composable internal fun ActivityExamples(values: ActivityPreviewValues, onPreview: () -> Unit) {
    val members = listOf(ActivityMember("Jordan", "JL"), ActivityMember("Sam", "SK"), ActivityMember("Alex", "AC"),
        ActivityMember("Mika", "MT"), ActivityMember("Rae", "RS"))
    Card {
        SectionHeader("Collection activity", "Small updates, shared progress.")
        AvatarGroup(members, "5 collaborators: Jordan, Sam, Alex, Mika, Rae", "+2", itemKey = { it.name }, maximumVisible = 3) {
            Avatar(it.name, it.initials, size = 40.dp)
        }
        TimelineItem("A new direction", "Today · Jordan", showsConnector = false) {
            ExpandableText(ActivityRecord(1).note, values.summaryExpanded, { values.summaryExpanded = it }, "Read update", "Collapse update")
        }
        NavLink("Open activity preview", onPreview, subtitle = "Pull to refresh, load pages, and retry.")
    }
}

@Composable internal fun ActivityPreviewScreen(values: ActivityPreviewValues, onBack: () -> Unit) {
    val t = FoundryTheme.tokens
    BackHandler(onBack = onBack)
    LaunchedEffect(values.request) { values.completeRequest() }
    DisposableEffect(values) { onDispose { values.cancelPending() } }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back to components") }
        RefreshContainer(values.refreshing, values::requestRefresh, Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().testTag("activity-list"), contentPadding = PaddingValues(t.space.page),
                verticalArrangement = Arrangement.spacedBy(t.space.section)) {
                item(key = "header") {
                    SectionHeader("Atlas activity", "Pull down to refresh. Local preview only.") {
                        ActionButton(values::requestRefresh, variant = ButtonVariant.QUIET,
                            enabled = !values.refreshing && values.phase != LoadMorePhase.LOADING) { Text("Refresh updates") }
                    }
                }
                item(key = "status") { Text("${values.records.size} updates · Refreshes: ${values.refreshes}", style = t.typography.caption) }
                item(key = "failure") { ToggleField("Fail next page", values.failNextPage, { values.failNextPage = it }) }
                items(values.records, key = { it.id }) { record ->
                    TimelineItem(record.title, record.timestamp, showsConnector = record.id != values.records.last().id) {
                        ExpandableText(record.note, record.id in values.expanded, { values.setExpanded(record.id, it) },
                            "Read update ${record.id}", "Collapse update ${record.id}")
                    }
                }
                item(key = "footer") {
                    LoadMoreFooter(values.phase, values.message,
                        if (values.phase == LoadMorePhase.FAILED) "Retry page" else "Load more updates", values::requestPage,
                        enabled = !values.refreshing)
                }
            }
        }
    }
}
