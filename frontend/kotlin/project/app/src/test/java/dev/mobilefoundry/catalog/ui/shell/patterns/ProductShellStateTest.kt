package dev.mobilefoundry.catalog.ui.shell.patterns

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProductShellStateTest {
    private val json = Json { encodeDefaults = true }
    @Test fun sharedScenariosCoverIndependentProfilesAdmissionAndBounds() {
        val scenarios = Json.parseToJsonElement(File(System.getProperty("product.shell.fixtures"), "scenarios.json").readText()).jsonArray
        for (scenario in scenarios) {
            var state = ProductShellState()
            for (entry in scenario.jsonObject.getValue("steps").jsonArray) {
                val step = entry.jsonObject
                val id = step["id"]?.jsonPrimitive?.content
                val action = when (step.getValue("action").jsonPrimitive.content) {
                    "daily" -> ProductShellAction.Daily(id!!)
                    "checkIn" -> ProductShellAction.CheckIn
                    "workspace" -> ProductShellAction.Workspace(id!!)
                    "workspaceBack" -> ProductShellAction.WorkspaceBack
                    "toggleDone" -> ProductShellAction.ToggleDone
                    "browse" -> ProductShellAction.Browse(id)
                    "toggleSaved" -> ProductShellAction.ToggleSaved
                    "tool" -> ProductShellAction.Tool(id!!)
                    "addLayer" -> ProductShellAction.AddLayer
                    "export" -> ProductShellAction.Export
                    "focus" -> ProductShellAction.Focus(id!!)
                    "nextStep" -> ProductShellAction.NextStep
                    "previousStep" -> ProductShellAction.PreviousStep
                    "resetSetup" -> ProductShellAction.ResetSetup
                    else -> error("Unknown fixture action")
                }
                repeat(step["repeat"]?.jsonPrimitive?.int ?: 1) { state = state.apply(action) }
                val snapshot = json.encodeToJsonElement(state.copy(completedItems = state.completedItems.sorted().toSet(), savedItems = state.savedItems.sorted().toSet())).jsonObject
                step.getValue("expect").jsonObject.forEach { (key, expected) ->
                    assertEquals("${scenario.jsonObject["name"]}: $step / $key", expected, snapshot[key])
                }
            }
        }
    }
    @Test fun saveableRoundTripRetainsIndependentProfileState() {
        var state = ProductShellState()
        listOf(ProductShellAction.CheckIn, ProductShellAction.Daily("Account"), ProductShellAction.CheckIn,
            ProductShellAction.Workspace("release"), ProductShellAction.ToggleDone,
            ProductShellAction.Browse("studio"), ProductShellAction.ToggleSaved,
            ProductShellAction.AddLayer, ProductShellAction.Export, ProductShellAction.NextStep).forEach { state = state.apply(it) }
        val restored = productShellStateSaver.restore(json.encodeToString(state))!!
        assertEquals(state, restored)
        assertEquals(2, restored.apply(ProductShellAction.CheckIn).checkIns["Account"])
    }
}
