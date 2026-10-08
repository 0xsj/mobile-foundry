package dev.mobilefoundry.catalog.ui.forms

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mobilefoundry.catalog.composition.*
import dev.mobilefoundry.query.isSubmitting
import dev.mobilefoundry.ui.components.layout.surface.Backdrop
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun FormsCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var provider by rememberSaveable { mutableStateOf(NotesProvider.MEMORY) }
    var scenario by rememberSaveable { mutableStateOf(CreateNoteScenario.SUCCESS) }
    val model: CreateNoteViewModel = viewModel {
        CreateNoteViewModel(CreateNoteComposition.creator(provider, scenario)) { Log.e("FoundryCreateNote", "Unexpected create-note error", it) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val tokens = FoundryTheme.tokens
    DisposableEffect(model) { onDispose { model.stop() } }
    Backdrop(Modifier.fillMaxSize(), background = {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(tokens.colors.surfaceGround.color)
            if (tokens.materials.style == FoundryThemeStyle.GLASS) {
                drawRect(Brush.linearGradient(listOf(tokens.colors.accent.color.copy(alpha = .24f),
                    Color.Transparent, tokens.colors.info.color.copy(alpha = .18f)),
                    start = Offset.Zero, end = Offset(size.width, size.height)))
                rotate(-24f) {
                    drawRect(tokens.colors.accent.color.copy(alpha = .22f),
                        topLeft = Offset(-size.width * .15f, size.height * .35f),
                        size = Size(size.width * .75f, size.height * .55f))
                }
            }
        }
    }) {
        Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Forms and mutations", style = tokens.typography.title)
            Surface(role = SurfaceRole.FLOATING) {
                Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                    Text("Provider", style = tokens.typography.label)
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        NotesProvider.entries.forEach { value ->
                            FilterChip(selected = provider == value, enabled = !state.mutation.isSubmitting, onClick = {
                                if (model.use(CreateNoteComposition.creator(value, scenario))) provider = value
                            }, label = { Text(value.label) })
                        }
                    }
                    Text("Scenario", style = tokens.typography.label)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        CreateNoteScenario.entries.forEach { value ->
                            FilterChip(selected = scenario == value, enabled = !state.mutation.isSubmitting, onClick = {
                                if (model.use(CreateNoteComposition.creator(provider, value))) scenario = value
                            }, label = { Text(value.label) })
                        }
                    }
                    Text("One form, two providers. HTTP responses are injected locally.", style = tokens.typography.caption)
                }
            }
            CreateNoteScreen(state, model::editTitle, model::blurTitle, submit = { model.submit() != null }, reset = model::reset)
        }
    }
}
