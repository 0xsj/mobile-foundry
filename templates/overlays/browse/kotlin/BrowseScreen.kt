package dev.mobilefoundry.product.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import dev.mobilefoundry.product.ProductState
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun ProductContent(state: ProductState, onStateChange: (ProductState) -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val collection = state.selectedCollection
    BackHandler(enabled = collection != null) { onStateChange(state.back()) }
    if (collection != null) DetailShell(Modifier.testTag("browse-detail"), header = {
        Column(Modifier.padding(t.space.page), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            ActionButton({ onStateChange(state.back()) }, modifier = Modifier.testTag("browse-back"), variant = ButtonVariant.QUIET) { Text("Back to collections") }
            SectionHeader(collection.title, "Collection overview")
        }
    }, content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page)) {
            Card(role = SurfaceRole.FLOATING) {
                Text("Your collection starts here.", style = t.typography.heading)
                Text("Add your products, media or directory entries to this screen.", style = t.typography.body)
            }
        }
    }, actions = {}) else VerticalStack(Modifier.fillMaxSize().testTag("browse-list").verticalScroll(rememberScrollState()).padding(t.space.page), spacing = t.space.section) {
        SectionHeader("Collections", "Find a place to explore.")
        SearchField("Find collections", state.filter, onValueChange = { onStateChange(state.copy(filter = it)) },
            clearLabel = "Clear search", modifier = Modifier.fillMaxWidth().testTag("browse-search"))
        if (state.visibleCollections.isEmpty()) Text("No collections match your search.", style = t.typography.body)
        state.visibleCollections.forEach { item -> key(item.id) {
            Card(role = SurfaceRole.FLOATING) {
                ActionButton({ focus.clearFocus(); keyboard?.hide(); onStateChange(state.select(item.id)) },
                    modifier = Modifier.testTag(item.id), variant = ButtonVariant.QUIET) { Text("Open ${item.title}") }
                Text("Replace this placeholder with your collection.", style = t.typography.caption)
            }
        } }
    }
}
