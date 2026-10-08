package dev.mobilefoundry.catalog.ui.graphics

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.graphics.gl.GPUEffectSurface
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurface
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale

@Composable
fun GPUEffectsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var effect by rememberSaveable { mutableStateOf(EffectKind.RIPPLE) }
    var quality by rememberSaveable { mutableStateOf(EffectQuality.BALANCED) }
    var strength by rememberSaveable { mutableFloatStateOf(.65f) }
    var x by rememberSaveable { mutableFloatStateOf(.5f) }
    var y by rememberSaveable { mutableFloatStateOf(.5f) }
    var animate by rememberSaveable { mutableStateOf(true) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    var backend by remember { mutableStateOf("Starting GPU…") }
    var statistics by remember { mutableStateOf<EffectStatistics?>(null) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val tokens = FoundryTheme.tokens
    val running = animate && !reduced && !tokens.motion.reduced
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("GPU effects", style = tokens.typography.title)
        Text("Drag the canvas to steer waves or rotate the orbit.", style = tokens.typography.caption)
        val problem = failure
        if (problem != null) {
            Text(problem.publicInfo().meta.message)
            Button(onClick = { failure = null; backend = "Starting GPU…"; statistics = null; attempt++ }) { Text("Try again") }
        } else key(attempt) {
            GPUEffectSurface(EffectSettings.make(effect, quality, strength, EffectPoint.make(x, y)), running,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.2f), onPoint = { x = it.x; y = it.y },
                onEvent = { when (it) {
                    is EffectEvent.Ready -> backend = it.backend
                    is EffectEvent.Statistics -> statistics = it.value
                    is EffectEvent.Failed -> failure = it.failure
                } }, onUnexpectedError = { Log.e("FoundryGraphics", "Unexpected GPU error", it) })
        }
        FoundrySurface {
            Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                Text("Effect", style = tokens.typography.label)
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    EffectKind.entries.forEach { value -> FilterChip(selected = effect == value, onClick = { effect = value }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                Text("Quality", style = tokens.typography.label)
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    EffectQuality.entries.forEach { value -> FilterChip(selected = quality == value, onClick = { quality = value }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                Text("Strength", style = tokens.typography.label)
                Slider(value = strength, onValueChange = { strength = it }, modifier = Modifier.semantics { contentDescription = "Effect strength" })
                Row { Text("Animate", Modifier.weight(1f)); Switch(checked = animate, onCheckedChange = { animate = it }, modifier = Modifier.semantics { contentDescription = "Animate" }) }
                Row { Text("Reduce motion preview", Modifier.weight(1f)); Switch(checked = reduced, onCheckedChange = { reduced = it }, modifier = Modifier.semantics { contentDescription = "Reduce motion preview" }) }
                OutlinedButton(onClick = { x = .5f; y = .5f }) { Text("Reset focus") }
                Text(if (running) "Animation running" else "Animation paused", style = tokens.typography.label)
                Text(String.format(Locale.ROOT, "Focus: %.2f, %.2f", x, y), style = tokens.typography.caption)
            }
        }
        Text(backend, style = tokens.typography.caption)
        statistics?.let {
            Text("Submitted: ${it.submittedFrames} · Target: ${it.width} × ${it.height}", style = tokens.typography.caption)
            Text(String.format(Locale.ROOT, "Submission rate: %.0f/s · Budget: %d/s", it.submissionsPerSecond, quality.framesPerSecond), style = tokens.typography.caption)
        }
        Text("Submission counters describe this canvas. Emulator results are not device GPU benchmarks.", style = tokens.typography.caption)
    }
}
