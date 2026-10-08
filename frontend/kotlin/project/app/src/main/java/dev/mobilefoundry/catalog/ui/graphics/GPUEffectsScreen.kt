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
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

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
    var progress by rememberSaveable { mutableFloatStateOf(.5f) }
    var playhead by rememberSaveable { mutableFloatStateOf(.32f) }
    var replaying by remember { mutableStateOf(false) }
    var replayGeneration by remember { mutableIntStateOf(0) }
    var samples by remember { mutableStateOf(fieldFixture(false)) }
    val tokens = FoundryTheme.tokens
    val running = animate && !reduced && !tokens.motion.reduced
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val ambient = running && effect != EffectKind.PARTICLES && effect != EffectKind.FIELD
    val replayActive = running && replaying && effect == EffectKind.PARTICLES && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(replayActive, quality, replayGeneration) {
        if (!replayActive) return@LaunchedEffect
        val clock = EffectClock()
        val start = playhead
        val generation = replayGeneration
        var previousFrame = 0L
        while (replaying && replayGeneration == generation && effect == EffectKind.PARTICLES && playhead < 1f) {
            withFrameNanos { now ->
                // A scrub/restart can arrive before LaunchedEffect cancellation is applied.
                if (!replaying || replayGeneration != generation || effect != EffectKind.PARTICLES) return@withFrameNanos
                if (previousFrame == 0L || now - previousFrame >= 1_000_000_000L / quality.framesPerSecond - 500_000L) {
                    playhead = minOf(1f, start + (clock.frame(now / 1_000_000_000.0, true) / 2.4).toFloat())
                    previousFrame = now
                }
            }
        }
        if (replayGeneration == generation) replaying = false
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("GPU effects", style = tokens.typography.title)
        Text(effect.useCase(), style = tokens.typography.caption)
        val problem = failure
        if (problem != null) {
            Text(problem.publicInfo().meta.message)
            Button(onClick = { failure = null; backend = "Starting GPU…"; statistics = null; attempt++ }) { Text("Try again") }
        } else key(attempt) {
            GPUEffectSurface(EffectSettings.make(effect, quality, strength, EffectPoint.make(x, y),
                progress = if (effect == EffectKind.PARTICLES) playhead else progress, samples = samples), ambient,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.2f), onPoint = { x = it.x; y = it.y },
                onEvent = { when (it) {
                    is EffectEvent.Ready -> backend = it.backend
                    is EffectEvent.Statistics -> statistics = it.value
                    is EffectEvent.Failed -> failure = it.failure
                } }, onUnexpectedError = { Log.e("FoundryGraphics", "Unexpected GPU error", it) })
        }
        Surface {
            Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                Text("Effect", style = tokens.typography.label)
                EffectKind.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        row.forEach { value -> FilterChip(selected = effect == value, onClick = { replaying = false; effect = value }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                    }
                }
                if (effect == EffectKind.LIQUID) {
                    Text("Progress: ${kotlin.math.round(progress * 100).toInt()}% — supplied by the feature", style = tokens.typography.label)
                    Slider(progress, { progress = it }, modifier = Modifier.semantics { contentDescription = "Progress" })
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        OutlinedButton(onClick = { progress = 0f }) { Text("Empty") }
                        OutlinedButton(onClick = { progress = .5f }) { Text("Half") }
                        OutlinedButton(onClick = { progress = 1f }) { Text("Full") }
                    }
                }
                if (effect == EffectKind.PARTICLES) {
                    val status = if (replaying) (if (replayActive) "Playing" else "Paused") else (if (playhead == 1f) "Completed" else "Static preview")
                    Text("Playhead: ${kotlin.math.round(playhead * 100).toInt()}% · $status", style = tokens.typography.label)
                    Slider(playhead, { replaying = false; playhead = it }, modifier = Modifier.semantics { contentDescription = "Celebration playhead" })
                    OutlinedButton(onClick = { playhead = 0f; replayGeneration++; replaying = true }, enabled = running) { Text("Replay celebration") }
                    Text("One 2.4-second burst. Enable animation to replay, or scrub a static frame.", style = tokens.typography.caption)
                }
                if (effect == EffectKind.FIELD) {
                    Text("Samples: ${samples.size}/${EffectSettings.MAXIMUM_FIELD_SAMPLES} · illustrative density", style = tokens.typography.label)
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        OutlinedButton(onClick = { samples = fieldFixture(false) }) { Text("Clusters") }
                        OutlinedButton(onClick = { samples = fieldFixture(true) }) { Text("Trail") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                        OutlinedButton(onClick = { samples = emptyList() }) { Text("Clear data") }
                        OutlinedButton(onClick = { samples = samples + EffectFieldSample.make(EffectPoint.make(x, y)) },
                            enabled = samples.size < EffectSettings.MAXIMUM_FIELD_SAMPLES) { Text("Add at focus") }
                    }
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
                Text(if (ambient || replayActive) "Animation running" else "Animation paused", style = tokens.typography.label)
                Text(String.format(Locale.ROOT, "Focus: %.2f, %.2f", x, y), style = tokens.typography.caption)
            }
        }
        Text(backend, style = tokens.typography.caption)
        statistics?.let {
            Text("Submitted: ${it.submittedFrames} · Target: ${it.width} × ${it.height}", style = tokens.typography.caption)
            if (effect == EffectKind.PARTICLES || effect == EffectKind.FIELD) {
                Text("Redraws follow input changes · Budget: ${quality.framesPerSecond}/s", style = tokens.typography.caption)
            } else {
                Text(String.format(Locale.ROOT, "Submission rate: %.0f/s · Budget: %d/s", it.submissionsPerSecond, quality.framesPerSecond), style = tokens.typography.caption)
            }
        }
        Text("Submission counters describe this canvas. Emulator results are not device GPU benchmarks.", style = tokens.typography.caption)
    }
}

private fun EffectKind.useCase(): String = when (this) {
    EffectKind.RIPPLE -> "Ripple · touch feedback and interactive wave fields. Drag to move the focus."
    EffectKind.ORBIT -> "Orbit · procedural 3D hero. Drag to steer the lit sphere and ring."
    EffectKind.FLOW -> "Flow · a quiet backdrop for music, wellness or onboarding. Drag to shift the ribbons."
    EffectKind.MATERIAL -> "Material · a reflective membership or collectible card. Drag to steer its tilt and light."
    EffectKind.LIQUID -> "Liquid · uploads, timers or goals. Progress is supplied; animation only moves the surface."
    EffectKind.PARTICLES -> "Particles · a finite achievement or purchase celebration. Drag to choose its origin; replay or scrub."
    EffectKind.FIELD -> "Field · density for fitness, analytics or availability. Drag to choose where to add a sample. Fixtures are illustrative."
}

private fun fieldFixture(trail: Boolean): List<EffectFieldSample> {
    val points = if (trail) listOf(Triple(.15f, .75f, .3f), Triple(.3f, .6f, .5f), Triple(.45f, .52f, .8f),
        Triple(.6f, .4f, 1f), Triple(.76f, .28f, .65f), Triple(.85f, .2f, .3f))
        else listOf(Triple(.3f, .35f, 1f), Triple(.4f, .45f, .75f), Triple(.72f, .65f, 1f), Triple(.65f, .72f, .6f))
    return points.map { EffectFieldSample.make(EffectPoint.make(it.first, it.second), it.third) }
}
