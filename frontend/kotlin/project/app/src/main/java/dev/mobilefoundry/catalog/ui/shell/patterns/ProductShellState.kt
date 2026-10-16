package dev.mobilefoundry.catalog.ui.shell.patterns

import kotlinx.serialization.Serializable

enum class ProductShellPattern(val label: String, val subtitle: String) {
    DAILY("Daily app", "A personal dashboard with four independent tabs."),
    WORKSPACE("Workspace", "A list and detail workspace that expands with the window."),
    BROWSE("Browse and collect", "Search a collection, open details and keep favorites."),
    CANVAS("Canvas studio", "A focused canvas with tools, an inspector and actions."),
    SETUP("Guided setup", "A short setup flow with a draft, review and completion.")
}
data class ShellSampleItem(val id: String, val title: String, val subtitle: String) {
    companion object {
        val workspace = listOf(ShellSampleItem("brief", "Project brief", "Shape an idea into a clear next step."),
            ShellSampleItem("research", "Research board", "Collect references and early findings."),
            ShellSampleItem("release", "Release checklist", "Keep a small launch moving forward."))
        val collections = listOf(ShellSampleItem("coast", "Coastal walks", "A quiet collection for time outdoors."),
            ShellSampleItem("city", "City notebooks", "Small observations from everyday places."),
            ShellSampleItem("studio", "Studio objects", "Useful forms, textures and materials."))
    }
}
sealed interface ProductShellAction {
    data class Daily(val id: String) : ProductShellAction
    data object CheckIn : ProductShellAction
    data class Workspace(val id: String) : ProductShellAction
    data object WorkspaceBack : ProductShellAction
    data object ToggleDone : ProductShellAction
    data class Browse(val id: String?) : ProductShellAction
    data object ToggleSaved : ProductShellAction
    data class Tool(val id: String) : ProductShellAction
    data object AddLayer : ProductShellAction
    data object Export : ProductShellAction
    data class Focus(val id: String) : ProductShellAction
    data object NextStep : ProductShellAction
    data object PreviousStep : ProductShellAction
    data object ResetSetup : ProductShellAction
}
/** Saveable fixture values; native field states remain owned separately above preview branches. */
@Serializable data class ProductShellState(
    val dailyPage: String = "Today", val checkIns: Map<String, Int> = emptyMap(),
    val workspaceItem: String = "brief", val workspaceDetail: Boolean = false, val completedItems: Set<String> = emptySet(),
    val browseItem: String? = null, val savedItems: Set<String> = emptySet(),
    val canvasTool: String = "Select", val layers: Int = 1, val exports: Int = 0,
    val setupFocus: String = "Explore", val setupStep: Int = 0, val setupFinished: Boolean = false,
) {
    fun apply(action: ProductShellAction): ProductShellState = when (action) {
        is ProductShellAction.Daily -> if (action.id in dailyPages) copy(dailyPage = action.id) else this
        ProductShellAction.CheckIn -> copy(checkIns = checkIns + (dailyPage to ((checkIns[dailyPage] ?: 0) + 1)))
        is ProductShellAction.Workspace -> if (ShellSampleItem.workspace.any { it.id == action.id }) copy(workspaceItem = action.id, workspaceDetail = true) else this
        ProductShellAction.WorkspaceBack -> copy(workspaceDetail = false)
        ProductShellAction.ToggleDone -> copy(completedItems = if (workspaceItem in completedItems) completedItems - workspaceItem else completedItems + workspaceItem)
        is ProductShellAction.Browse -> if (action.id == null || ShellSampleItem.collections.any { it.id == action.id }) copy(browseItem = action.id) else this
        ProductShellAction.ToggleSaved -> browseItem?.let { copy(savedItems = if (it in savedItems) savedItems - it else savedItems + it) } ?: this
        is ProductShellAction.Tool -> if (action.id in tools) copy(canvasTool = action.id) else this
        ProductShellAction.AddLayer -> if (layers < 8) copy(layers = layers + 1) else this
        ProductShellAction.Export -> copy(exports = exports + 1)
        is ProductShellAction.Focus -> if (!setupFinished && action.id in focuses) copy(setupFocus = action.id) else this
        ProductShellAction.NextStep -> if (setupFinished) this else if (setupStep < 2) copy(setupStep = setupStep + 1) else copy(setupFinished = true)
        ProductShellAction.PreviousStep -> if (!setupFinished && setupStep > 0) copy(setupStep = setupStep - 1) else this
        ProductShellAction.ResetSetup -> copy(setupStep = 0, setupFinished = false, setupFocus = "Explore")
    }
    companion object {
        val dailyPages = listOf("Today", "Library", "Activity", "Account")
        val tools = listOf("Select", "Draw", "Arrange")
        val focuses = listOf("Explore", "Build", "Learn")
    }
}
