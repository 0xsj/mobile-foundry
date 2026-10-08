package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.feedback.alert.InlineAlert
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.feedback.progress.ProgressIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.overlays.dialog.ConfirmationDialog
import dev.mobilefoundry.ui.components.layout.surface.Backdrop
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.tabs.Tabs
import dev.mobilefoundry.ui.components.patterns.pageheader.PageHeader
import dev.mobilefoundry.ui.components.patterns.selectioncard.SelectionCard
import dev.mobilefoundry.ui.components.patterns.settingssection.SettingsSection
import dev.mobilefoundry.ui.styles.tokens.FoundryAppearance
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

private enum class ComponentGroup(val label: String) {
    ACTIONS("Actions"), CONTENT("Content"), PATTERNS("Patterns"), CONTROLS("Controls"), OVERLAYS("Overlays"),
    DISPLAY("Display"), FEEDBACK("Feedback"), COLLECTIONS("Collections"), CONTEXT("Context"), LAYOUT("Layout")
}

@Composable
fun ComponentCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var glass by rememberSaveable { mutableStateOf(true) }
    var details by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    if (details) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            ComponentDetailScreen(onBack = { details = false })
        }
        return
    }
    stateHolder.SaveableStateProvider("componentExamples") {
        Column(modifier.fillMaxSize()) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Components", style = FoundryTheme.tokens.typography.title)
            Row {
                FilterChip(dark, { dark = !dark }, label = { Text("Dark preview") })
                Spacer(Modifier.width(FoundryTheme.tokens.space.inline))
                FilterChip(glass, { glass = !glass }, label = { Text("Glass preview") })
            }
            FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
                style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
                ComponentExamples(onNavigate = { details = true }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentExamples(onNavigate: () -> Unit, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    var group by rememberSaveable { mutableStateOf(ComponentGroup.ACTIONS) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var quality by rememberSaveable { mutableStateOf("Balanced") }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var removed by rememberSaveable { mutableStateOf(false) }
    var activityUpdates by rememberSaveable { mutableStateOf(true) }
    var photos by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf("Original") }
    var destination by rememberSaveable { mutableStateOf("This device") }
    var intensity by rememberSaveable { mutableFloatStateOf(0.5f) }
    var reviewDate by rememberSaveable { mutableLongStateOf(1791417600000L) } // 2026-10-08 UTC calendar date
    var workspaceSheet by rememberSaveable { mutableStateOf(false) }
    var resetCopies by rememberSaveable { mutableStateOf(false) }
    var copies by rememberSaveable { mutableIntStateOf(0) }
    var lastAction by rememberSaveable { mutableStateOf("No action yet") }
    var artwork by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) } // Transient presentation is not replayed after restoration.
    var undos by rememberSaveable { mutableIntStateOf(0) }
    var retries by rememberSaveable { mutableIntStateOf(0) }
    var collectionSearch by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var collectionSort by rememberSaveable { mutableStateOf("Name") }
    var selectedProjects by rememberSaveable { mutableStateOf(listOf<String>()) }
    var workspaceTitle by rememberSaveable { mutableStateOf("Atlas") }
    var ownerName by rememberSaveable { mutableStateOf("Jordan") }
    var fieldsValidated by rememberSaveable { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var choices by rememberSaveable { mutableIntStateOf(0) }
    var narrow by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf("None") }
    val projects = listOf("Atlas workspace", "Orbit study", "Field notes")
    Backdrop(modifier.fillMaxWidth(), background = {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.surfaceGround.color,
            t.colors.accentTint.color, t.colors.surfaceGround.color))))
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page),
            verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            PageHeader("Everyday interfaces", "Simple controls, useful compositions, and room for your own content.") {
                Badge("33 building blocks", tone = MessageTone.INFO)
            }
            Tabs("Component families", ComponentGroup.entries, group,
                { group = it; notice = 0; help = false; options = false }, label = { it.label })
            when (group) {
                ComponentGroup.ACTIONS -> {
                    Card(role = SurfaceRole.FLOATING) {
                        Heading("Actions")
                        ActionButton({ count++ }, Modifier.fillMaxWidth(), isBusy = busy) { Text(if (busy) "Saving changes…" else "Save changes") }
                        ActionButton({ count++ }, variant = ButtonVariant.SECONDARY) { Text("Preview") }
                        ActionButton({ count++ }, variant = ButtonVariant.QUIET) { Text("Learn more") }
                        ActionButton({ count++ }, enabled = false) { Text("Unavailable") }
                        ActionButton({ confirmDelete = true }, variant = ButtonVariant.DESTRUCTIVE) { Text("Remove example") }
                        ListRow("Show busy state", trailing = {
                            Switch(busy, { busy = it }, Modifier.semantics { contentDescription = "Show busy state" })
                        })
                        Text("Actions performed: $count", style = t.typography.caption)
                        if (removed) Badge("Example item removed", tone = MessageTone.WARNING)
                    }
                    Card {
                        Heading("Search a collection")
                        SearchField("Search projects", search, { search = it }, "Clear search", Modifier.fillMaxWidth())
                        val matches = projects.filter { search.isEmpty() || it.contains(search, ignoreCase = true) }
                        if (matches.isEmpty()) {
                            EmptyState("No matching projects", "Try another name or clear your search.", actions = {
                                ActionButton({ search = "" }, variant = ButtonVariant.SECONDARY) { Text("Reset search") }
                            })
                        } else matches.forEach { ListRow(it, "Personal project") }
                    }
                }
                ComponentGroup.CONTENT -> {
                    Card {
                        Heading("Status and rows")
                        // A column lets long/localized labels grow without horizontal clipping.
                        Badge("Draft"); Badge("Ready", tone = MessageTone.INFO)
                        Badge("Pending", tone = MessageTone.WARNING); Badge("Needs attention", tone = MessageTone.CRITICAL)
                        HorizontalDivider()
                        ListRow("Atlas workspace", "A place for your next idea", Modifier.clickable { showDetails = true })
                        HorizontalDivider()
                        ListRow("Storage", "This device", trailing = { Badge("Local") })
                    }
                    InlineAlert("Changes stay on this device", "Connect an account when you are ready to share your workspace.") {
                        ActionButton({ showDetails = true }, variant = ButtonVariant.QUIET) { Text("Learn about accounts") }
                    }
                    InlineAlert("Review before continuing", "Some changes still need your attention.", tone = MessageTone.WARNING)
                    InlineAlert("Could not complete the action", "Your draft is still available. Try again when you are ready.", tone = MessageTone.CRITICAL)
                    Card {
                        ProgressIndicator("Preparing preview", fraction = 0.65f)
                        ProgressIndicator("Waiting for connection")
                    }
                    Card {
                        EmptyState("Your collection starts here", "Add an idea, an image, or a project to make it yours.", actions = {
                            ActionButton({ count++ }) { Text("Create something") }
                        })
                    }
                }
                ComponentGroup.PATTERNS -> {
                    SettingsSection("Preferences", footer = "Settings are examples and stay in this gallery.", role = SurfaceRole.FLOATING) {
                        ListRow("Notifications", trailing = {
                            Switch(notifications, { notifications = it }, Modifier.semantics { contentDescription = "Notifications" })
                        })
                        HorizontalDivider()
                        ListRow("Appearance", "Follows your preview settings", trailing = {
                            Badge(if (t.appearance == FoundryAppearance.DARK) "Dark" else "Light")
                        })
                    }
                    Heading("Preview quality")
                    listOf("Balanced", "Detailed").forEach { option ->
                        SelectionCard(quality == option, { quality = option }) {
                            Text(option, style = t.typography.label)
                            Text(if (option == "Balanced") "A lighter preview for everyday work." else "More detail when you need a closer look.", color = t.colors.inkSecondary.color)
                        }
                    }
                    Text("Selected quality: $quality", style = t.typography.caption)
                    Card(role = SurfaceRole.FLOATING) {
                        PageHeader("Atlas workspace", "A page header can carry your own actions.") {
                            ActionButton({ showDetails = true }, variant = ButtonVariant.SECONDARY) { Text("Open details") }
                        }
                    }
                }
                ComponentGroup.CONTROLS -> {
                    ControlExamples(activityUpdates, { activityUpdates = it },
                        photos, { photos = it }, notes, { notes = it }, format, { format = it },
                        destination, { destination = it }, intensity, { intensity = it }, reviewDate, { reviewDate = it })
                    FieldGroupExample(workspaceTitle, { workspaceTitle = it }, ownerName, { ownerName = it }, fieldsValidated, { fieldsValidated = true })
                }
                ComponentGroup.OVERLAYS -> OverlayExamples(copies, lastAction,
                    onShowSheet = { workspaceSheet = true }, onResetRequest = { resetCopies = true },
                    onDuplicate = { copies++; lastAction = "Workspace duplicated" })
                ComponentGroup.DISPLAY -> DisplayExamples(artwork, { artwork = it })
                ComponentGroup.FEEDBACK -> FeedbackExamples(loading, { loading = it }, reduced, { reduced = it },
                    notice, { notice = it }, undos, retries, { undos++ }, { retries++ })
                ComponentGroup.COLLECTIONS -> CollectionExamples(collectionSearch, { collectionSearch = it }, favorites, { favorites = it },
                    collectionSort, { collectionSort = it }, selectedProjects, { selectedProjects = it })
                ComponentGroup.CONTEXT -> ContextExamples(help, { help = it }, options, { options = it }, choices,
                    { choices++ }, { help = false; options = false; onNavigate() })
                ComponentGroup.LAYOUT -> LayoutExamples(narrow, { narrow = it }, picked, { picked = it })
            }
        }
    }
    SheetPanel("Project details", showDetails, { showDetails = false }, "Close details") {
        Card { ListRow("Atlas workspace", "Updated just now") }
    }
    ConfirmationDialog("Remove this example item?", "This only changes the gallery example.",
        confirmDelete, { confirmDelete = false }, "Remove item", "Cancel", { removed = true }, destructive = true)
    SheetPanel("Workspace sheet", workspaceSheet, { workspaceSheet = false }, "Close workspace sheet") {
        Card { ListRow("Atlas workspace", "Custom content in a native presentation.") }
        Text("Closing this sheet does not change your selection.")
    }
    ConfirmationDialog("Reset workspace copies?", "Clear the example copy count. Your real projects are not affected.",
        resetCopies, { resetCopies = false }, "Confirm reset", "Keep copies",
        { copies = 0; lastAction = "Copies reset" }, destructive = true)
}

@Composable private fun Heading(text: String) {
    Text(text, style = FoundryTheme.tokens.typography.heading, modifier = Modifier.semantics { heading() })
}
